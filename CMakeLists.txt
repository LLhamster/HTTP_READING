# cmake_minimum_required(VERSION 3.10)
# project(server)


# # 设置 C++ 标准
# set(CMAKE_CXX_STANDARD 17)
# set(CMAKE_CXX_STANDARD_REQUIRED True)

# include_directories(/usr/include/hiredis /usr/local/include)
# link_directories(/usr/lib/x86_64-linux-gnu /usr/local/lib)

# # 添加可执行文件
# add_executable(server main.cpp util.cpp pthread.cpp epoll.cpp logger.cpp)

# # 添加 pthread 选项
# find_package(Threads REQUIRED)
# target_link_libraries(server PRIVATE Threads::Threads redis++ hiredis pthread)


# # -----------------------------
# # MySQL Connector/C++ 配置
# # -----------------------------

# # 1. 指定 MySQL Connector/C++ 的头文件路径
# # 假设 Linux 默认安装路径是 /usr/include/cppconn
# target_include_directories(server PRIVATE /usr/include/cppconn)

# # 2. 链接 MySQL Connector/C++ 库
# # Linux 下通常是 mysqlcppconn
# target_link_libraries(server PRIVATE mysqlcppconn)

# # 添加编译选项
# # target_compile_options(server PRIVATE -Wall -O2)


cmake_minimum_required(VERSION 3.10)
project(server)

# 设置 C++ 标准
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED True)

# -----------------------------
# include 头文件路径
# -----------------------------
include_directories(
    /usr/include/hiredis
    /usr/local/include
    /usr/include/jsoncpp        # jsoncpp 头文件路径
    /usr/include/x86_64-linux-gnu  # curl 头文件路径
)

# -----------------------------
# link 库文件路径
# -----------------------------
link_directories(
    /usr/lib/x86_64-linux-gnu
    /usr/local/lib
)

# -----------------------------
# 添加可执行文件
# -----------------------------
add_executable(server main.cpp util.cpp pthread.cpp epoll.cpp logger.cpp)

# -----------------------------
# pthread
# -----------------------------
find_package(Threads REQUIRED)
target_link_libraries(server PRIVATE Threads::Threads pthread)

# -----------------------------
# redis++
# -----------------------------
target_link_libraries(server PRIVATE redis++ hiredis)

# -----------------------------
# MySQL Connector/C++
# -----------------------------
target_include_directories(server PRIVATE /usr/include/cppconn)
target_link_libraries(server PRIVATE mysqlcppconn)

# -----------------------------
# curl 和 jsoncpp
# -----------------------------
find_package(CURL REQUIRED)
find_package(PkgConfig REQUIRED)
pkg_check_modules(JSONCPP jsoncpp)

target_include_directories(server PRIVATE ${CURL_INCLUDE_DIRS} ${JSONCPP_INCLUDE_DIRS})
target_link_libraries(server PRIVATE ${CURL_LIBRARIES} ${JSONCPP_LIBRARIES})

