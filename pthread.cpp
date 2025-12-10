#include "pthread.h"
#include <pthread.h>
#include <stdio.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <sys/socket.h>
#include <stdlib.h>
#include <fcntl.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include "util.h"
#include <stdint.h>
#include <errno.h>
#include <signal.h>
#include <string>
#include "client_count.h"
#include <unordered_set>
#include <vector>
#include <iostream>
#include <sstream>
#include <iomanip>

#include <iostream>
#include <memory>
#include <string>
#include <sstream>
#include <vector>

#include <mysql_driver.h>
#include <mysql_connection.h>
#include <cppconn/exception.h>
#include <cppconn/prepared_statement.h>
#include <cppconn/resultset.h>
#include <sw/redis++/redis++.h>

#include <curl/curl.h>
#include <json/json.h>
#include <fstream>



pthread_mutex_t pthread::lock_ = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t pthread::cond_ = PTHREAD_COND_INITIALIZER;
int pthread::front_ = 0;
int pthread::rear_ = 0;
pthread_t pthread::workers_[WORKER_NUM];
int pthread::queue_[1024];


static std::string html; // 用 std::string 存储动态内容

void load_html(const std::string& path) {
    std::ifstream file(path);
    if (!file.is_open()) {
        html = "<h1>加载失败:无法打开HTML文件</h1>";
        return;
    }
    std::ostringstream ss;
    ss << file.rdbuf();
    html = ss.str();
}

static void drain_read(int fd) {
    char buf[4096];
    // //将设备数减去1
    // pthread_mutex_lock(&client_count_mutex);
    // client_count--;
    // pthread_mutex_unlock(&client_count_mutex);

    for (;;) {
        ssize_t n = recv(fd, buf, sizeof(buf), 0);
        if (n > 0) continue;
        if (n == 0) break; // 对端关闭
        if (errno == EINTR) continue;
        if (errno == EAGAIN || errno == EWOULDBLOCK) break;
        break;
    }
}


int pthread::pthread_init(int epollfd){
    for(int j = 0; j < WORKER_NUM; j++){
        pthread_create(&workers_[j], NULL, worker_, (void*)(intptr_t)epollfd);
    }
    return 0;
}

//处理线程
static std::unordered_set<int> clients;
static pthread_mutex_t clients_mutex = PTHREAD_MUTEX_INITIALIZER;

static bool read_until(int fd, std::string& buf,  const char* mask){
    while(1){
        char tmp[4096];
        int num = recv(fd, tmp, sizeof(tmp), 0);
        // printf("read num=%d\n", num);
        if(num > 0){
            buf.append(tmp, tmp+num);
            if(buf.find(mask) != std::string::npos){
                return true;
            }
        }
        else if(num == 0) return true;
        else{
            if(errno ==EAGAIN || errno == EWOULDBLOCK) continue;
            if(errno == EINTR) continue;
            return false;
        }
    }
    
}

static ssize_t write_all(int fd, const char* buf, size_t len){
    size_t total = 0;
    while(total < len){
        ssize_t n = send(fd, buf+total, len-total, 0);
        if(n>0) total += n;
        else if(n==0) break;
        else{
            if(errno == EINTR) continue;
            if(errno == EAGAIN || errno == EWOULDBLOCK) continue;
            return -1;
        }
    }
    return total;
}

static void respond_404(int fd){
    const char* header = 
        "HTTP/1.1 404 Not Found\r\n"
        "Content-Length: 13\r\n"
        "Connection: close\r\n"
        "\r\n"
        "404 Not Found";
    write_all(fd, header, strlen(header));
    shutdown(fd, SHUT_WR);
    drain_read(fd);
    close(fd);
}

static void respond_html(int fd){

    load_html("/home/hamster/coding/http_reading/web/bookstore.html"); // html 是 std::string

    // 拼接 HTTP header
    std::string header = "HTTP/1.1 200 OK\r\n";
    header += "Content-Length: " + std::to_string(html.size()) + "\r\n";
    header += "Content-Type: text/html; charset=utf-8\r\n";
    header += "Connection: close\r\n\r\n";

    // 发送 header
    if(write_all(fd, header.c_str(), header.size()) < 0 ||
    write_all(fd, html.c_str(), html.size()) < 0){
        perror("write error");
        respond_404(fd);
        return;
    }
    shutdown(fd, SHUT_WR);
    drain_read(fd);
    close(fd);
}

static void broadcast(int fd, const char* msg, const char* url = nullptr){
    std::vector<int> to_close;
    // 按照 SSE 协议包装消息：每条消息使用 data: 开头，并以空行结束,不按照就没法显示。
    std::string payload;
    if (url) {
        // 如果提供了 URL，则构造一个按钮
        payload = "data: <button onclick=\"window.location.href='" + std::string(url) + "'\">" + std::string(msg) + "</button>\n\n";
    } else {
        // 普通消息
        payload = "data: " + std::string(msg) + "\n\n";
    }
    pthread_mutex_lock(&clients_mutex);
    for(int client_fd : clients){
        if(write_all(client_fd, payload.c_str(), payload.size()) < 0){
            to_close.push_back(client_fd);
            printf("shibai");
        }
    }
    for(int i :to_close){
        auto it = clients.find(i);
        if(it != clients.end()) clients.erase(it);
    }
    pthread_mutex_unlock(&clients_mutex);
    for(int i: to_close){
        //将设备数减去1
        pthread_mutex_lock(&client_count_mutex);
        client_count--;
        pthread_mutex_unlock(&client_count_mutex);
        shutdown(i, SHUT_WR);
        drain_read(i);
        close(i);
    }
}

static void keep_html(int fd){
    const char* header = 
        "HTTP/1.1 200 OK\r\n"
        "Content-Type: text/event-stream\r\n"
        "Cache-Control: no-cache\r\n"
        "Connection: keep-alive\r\n"
        "\r\n";
    if(write_all(fd, header, strlen(header)) < 0){
        perror("write error");
        respond_404(fd);
        return;
    }
    pthread_mutex_lock(&clients_mutex);
    clients.insert(fd);
    pthread_mutex_unlock(&clients_mutex);
    pthread_mutex_lock(&client_count_mutex);
    client_count++;
    pthread_mutex_unlock(&client_count_mutex);

    broadcast(fd, "[系统] 欢迎新用户进入聊天室！");
    // broadcast(fd, "点击访问 PDF 文档", "http://127.0.0.1/books/1/main.pdf");
}

static std::string decodeMsg(const std::string &msg){
    std::string tmp;
    int ii;
    for(int i=0;i < msg.length();i++){
        if(msg[i] == '%'){
            std::istringstream iss(msg.substr(i+1, 2));
            iss >> std::hex >> ii;
            char ch = static_cast<char>(ii);
            tmp += ch;
            i += 2;
        }
        else if(msg[i] == '+') tmp += ' ';
        else tmp += msg[i];
    }
    return tmp;
}

static std::string searchData_sql(const std::string &title){
    // 请根据实际情况修改下面的连接信息
    const std::string dbUser = "root";
    const std::string dbPass = "123";
    const std::string dbName = "bookstore"; // 你的数据库名
    const std::string socketPath = "/var/run/mysqld/mysqld.sock"; // 根据系统调整

    sql::mysql::MySQL_Driver* driver = sql::mysql::get_mysql_driver_instance();

    // 使用 ConnectOptionsMap 指定 Unix socket（localhost 的本地套接字）
    sql::ConnectOptionsMap connection_properties;
    connection_properties["hostName"] = sql::SQLString("localhost");
    connection_properties["userName"] = sql::SQLString(dbUser);
    connection_properties["password"] = sql::SQLString(dbPass);
    connection_properties["schema"] = sql::SQLString(dbName);
    connection_properties["socket"] = sql::SQLString(socketPath);
    // 可选：设置超时等
    // connection_properties["OPT_CONNECT_TIMEOUT"] = 10;

    std::unique_ptr<sql::Connection> conn(driver->connect(connection_properties));

    // 确保使用 utf8mb4，避免中文乱码
    {
        std::unique_ptr<sql::Statement> st(conn->createStatement());
        st->execute("SET NAMES utf8mb4");
    }

    std::unique_ptr<sql::PreparedStatement> ps(
        conn->prepareStatement("SELECT url FROM books_simple WHERE title = ? LIMIT 1")
    );
    ps->setString(1, title);
    std::unique_ptr<sql::ResultSet> rs(ps->executeQuery());

    if (rs->next()) {
        std::string url = rs->getString("url");
        //写回Redis缓存
        sw::redis::Redis redis("tcp://127.0.0.1:6379");
        redis.set(title.c_str(), url.c_str(), std::chrono::seconds(3600));
        return url;
    } else {
        std::cout << "Not found: " << title << "\n";
        return "none";
    }
    
}

static auto search_redis(const std::string &title){
    // 连接到 Redis
    sw::redis::Redis redis("tcp://127.0.0.1:6379");

    // // 设置书名与路径
    // redis.hset("books", "三体", "books/2/main.pdf");
    // redis.hset("books", "Harry Potter", "books/harry_potter.pdf");

    // 获取路径
    auto path = redis.get(title.c_str());
    return path;
}

inline size_t onWriteData(void * buffer, size_t size, size_t nmemb, void * userp)
{
    std::string * str = dynamic_cast<std::string *>((std::string *)userp);
    str->append((char *)buffer, size * nmemb);
    return nmemb;
}


// 从 JSON 字符串中提取 content 字段内容（简单方法，不完全通用）
std::string extractContent(const std::string& json_str) {
    const std::string key = "\"content\":\"";
    size_t start = json_str.find(key);
    if (start == std::string::npos) return "未找到 content";
    start += key.length();
    size_t end = json_str.find("\"", start);
    if (end == std::string::npos) return "content 截取失败";

    std::string content = json_str.substr(start, end - start);

    // 处理转义字符（例如 \n \")
    std::string result;
    for (size_t i = 0; i < content.size(); ++i) {
        if (content[i] == '\\' && i + 1 < content.size()) {
            char next = content[i + 1];
            if (next == 'n') result += '\n';
            else if (next == '\"') result += '\"';
            else result += next;
            ++i;
        } else {
            result += content[i];
        }
    }
    return result;
}

static std::string model_request(const std::string &question){
    std::string result;
    CURL *curl;
    CURLcode res;
    curl = curl_easy_init();
    if(curl) {
        curl_easy_setopt(curl, CURLOPT_CUSTOMREQUEST, "POST");
        curl_easy_setopt(curl, CURLOPT_URL, "https://qianfan.baidubce.com/v2/chat/completions");
        curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);
        curl_easy_setopt(curl, CURLOPT_DEFAULT_PROTOCOL, "undefined");
        struct curl_slist *headers = NULL;
        headers = curl_slist_append(headers, "Content-Type: application/json");
        headers = curl_slist_append(headers, "appid: ");
        headers = curl_slist_append(headers, "Authorization: Bearer bce-v3/ALTAK-QvmHMcoLhKuv5kll60Eog/e51dc6d44a75b7a3cf36907a0a2f130d92f549ba");
        curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
        std::string data = "{\"model\":\"deepseek-v3.1-250821\","
                       "\"messages\":[{\"role\":\"user\",\"content\":\"" + question + "\"}],"
                       "\"stream\":false,\"enable_thinking\":false}";

        // const char *data = "{\"model\":\"deepseek-v3.1-250821\",\"messages\":[{\"role\":\"user\",\"content\":\"你好，你是谁？\"}],\"stream\":false,\"enable_thinking\":false}";


        const char* post_data = data.c_str();  // 转为 const char* 传给 curl
        curl_easy_setopt(curl, CURLOPT_POSTFIELDS, post_data);
        // curl_easy_setopt(curl, CURLOPT_POSTFIELDS, data);
        curl_easy_setopt(curl, CURLOPT_WRITEDATA, &result);
        curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, onWriteData);
        res = curl_easy_perform(curl);
        // std::cout<<result<< std::endl;
    }
    curl_easy_cleanup(curl);
    // 提取 content
    return extractContent(result);
}



static void handleMsg(int fd, std::string &msg){
    auto it = msg.find("msg=");
    if(it != std::string::npos){
        std::cout << "cut" << std::endl;
        msg = msg.substr(it+4);
    }
    std::string decoMsg = decodeMsg(msg);
    // auto path_tmp = search_redis(decoMsg);
    // std::string path;
    // if(path_tmp){
    //     path = *path_tmp;
    //     // path = "/" + path;
    // }
    // else{
    //     path = searchData_sql(decoMsg);
    // }
    // if(path != "none"){
    //     std::string url = "http://127.0.0.1" + path;
    //     // std::cout << "Found: " << url << "\n";
    //     broadcast(fd, decoMsg.c_str(),url.c_str());
    // }
    std::string model_answer = model_request(decoMsg.c_str());
    
    const std::string header = 
        "HTTP/1.1 200 OK\r\n"
        "Content-Length: " + std::to_string(model_answer.size()) + "\r\n"
        "Content-Type: text/html;charset=utf-8\r\n"
        "Connection: close\r\n\r\n";
    
    if(write_all(fd, header.c_str(), header.size()) < 0 ||
        write_all(fd, model_answer.c_str(), model_answer.size()) < 0){
        perror("write error");
        respond_404(fd);
        return;
    }

    shutdown(fd, SHUT_WR);
    drain_read(fd);
    close(fd);
}

static void ReturnCover(int fd, std::string& msg){
    //url_pdf是图书的路径，url_jpg是图书封面的路径，
    std::string url_jpg = "?msg=/coding/http_reading/sample.jpg";
    std::string url_pdf = "https://www.baidu.com";
    std::string title = "图书库中没有此内容";
    //提取前端发过来的信息
    auto it = msg.find("msg=");
    if(it != std::string::npos){
        msg = msg.substr(it+4);
    }
    auto pos = msg.find(' '); // 找到空格，表示 HTTP 协议开始
    if (pos != std::string::npos) {
        msg = msg.substr(0, pos);
    }
    std::string decoMsg = decodeMsg(msg);
    //查找是否有前端发过来的图书名
    auto path_tmp = search_redis(decoMsg);
    std::string path;
    if(path_tmp){
        path = *path_tmp;
        // path = "/" + path;
    }
    else{
        path = searchData_sql(decoMsg);
    }
    if(path != "none"){
        title = decoMsg;
        url_pdf = "http://127.0.0.1" + path + "main.pdf";
        url_jpg = "?msg=/file" + path + "main.jpg";
    }

    std::string html =
                "<!DOCTYPE html><html><body>"
                "<h2>" + title + "</h2>"
                "<a href=\"" + url_pdf +"\">"
                "<img src='/image"+ url_jpg +"' style='width:200px;border-radius:10px;'>"
                "</a>"
                "</body></html>";

    std::string response =
        "HTTP/1.1 200 OK\r\n"
        "Content-Type: text/html; charset=utf-8\r\n"
        "Content-Length: " + std::to_string(html.size()) + "\r\n"
        "Connection: close\r\n\r\n" +
        html;

    if(write_all(fd, response.c_str(), response.size()) < 0 ){
        perror("write error");
        respond_404(fd);
        return;
    }
    shutdown(fd, SHUT_WR);
    drain_read(fd);
    close(fd);
}

static void SendImage(int fd , std::string&msg){
    auto it = msg.find("msg=");
    if(it != std::string::npos){
        msg = msg.substr(it+4);
    }
    auto pos = msg.find(' '); // 找到空格，表示 HTTP 协议开始
    if (pos != std::string::npos) {
        msg = msg.substr(0, pos);
    }
    std::string decoMsg = decodeMsg(msg);
    std::string path = "/home/hamster";  // const char* 会隐式转 string
    path += decoMsg;
    // path += "main.jpg";
    std::ifstream file(path, std::ios::binary);
    std::ostringstream oss;
    oss << file.rdbuf();
    std::string image_data = oss.str();

    // 返回HTTP响应头 + 图片二进制数据
    std::string response =
        "HTTP/1.1 200 OK\r\n"
        "Content-Type: image/jpeg\r\n"
        "Content-Length: " + std::to_string(image_data.size()) + "\r\n"
        "Connection: close\r\n\r\n";
    if(write_all(fd, response.c_str(), response.size()) < 0 ||
    write_all(fd, image_data.c_str(), image_data.size()) < 0){
        perror("write error");
        respond_404(fd);
        return;
    }
    shutdown(fd, SHUT_WR);
    drain_read(fd);
    close(fd);
}

void* pthread::worker_(void* arg){
        while(1){
            int work_fd = dqueue();
            std::string buf;
            buf.reserve(4096);
            if(!read_until(work_fd, buf, "\r\n\r\n")) {
                perror("read error");
                close(work_fd);
                continue;
            }

            //打印请求命令

            std::cout << buf << std::endl;

            if(buf.find("GET /cover") != std::string::npos){
                ReturnCover(work_fd, buf);
                continue;
            }

            else if(buf.find("GET /image") != std::string::npos){
                SendImage(work_fd, buf);
                continue;
            }    
            
            else if(buf.find("/events") != std::string::npos){
                keep_html(work_fd);
                continue;
            }

            else if(buf.find("GET /") != std::string::npos){
                respond_html(work_fd);
                continue;
            }

            else if(buf.find("POST /chat") != std::string::npos){
                // std::cout<<"post"<<std::endl;
                handleMsg(work_fd, buf);
                continue;
            }

            
        }
        return NULL;
}

int pthread::dqueue() {
    pthread_mutex_lock(&lock_);
    while(front_ == rear_){
        pthread_cond_wait(&cond_, &lock_);
    }
    int fd = queue_[front_];
    front_ = (front_ + 1) % 1024;
    pthread_mutex_unlock(&lock_);
    return fd;
}

void pthread::enque(int fd) {
    pthread_mutex_lock(&lock_);
    queue_[rear_] = fd;
    rear_ = (rear_ + 1)%1024;
    pthread_cond_signal(&cond_);
    pthread_mutex_unlock(&lock_);
}