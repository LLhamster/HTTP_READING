#include "logger.h"

#include <atomic>
#include <condition_variable>
#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <mutex>
#include <queue>
#include <string>
#include <thread>
#include <vector>
#include <chrono>

namespace {
    struct LogMsg {
        std::string level;
        std::string text;
    };

    std::mutex g_mu;
    std::condition_variable g_cv;
    std::queue<LogMsg> g_q;
    std::thread g_worker;
    std::atomic<bool> g_running{false};
    std::ofstream g_out;
    std::size_t g_capacity = 8192;

    std::string now_string() {
        using namespace std::chrono;
        auto tp = system_clock::now();
        std::time_t t = system_clock::to_time_t(tp);
        char buf[64];
        std::tm tm;
#if defined(_WIN32)
        localtime_s(&tm, &t);
#else
        localtime_r(&t, &tm);
#endif
        std::snprintf(buf, sizeof(buf), "%04d-%02d-%02d %02d:%02d:%02d",
                      tm.tm_year + 1900, tm.tm_mon + 1, tm.tm_mday,
                      tm.tm_hour, tm.tm_min, tm.tm_sec);
        return buf;
    }

    void worker_loop() {
        while (g_running.load(std::memory_order_acquire)) {
            std::unique_lock<std::mutex> lk(g_mu);
            g_cv.wait(lk, []{ return !g_q.empty() || !g_running.load(); });
            while (!g_q.empty()) {
                auto m = std::move(g_q.front());
                g_q.pop();
                lk.unlock();
                if (g_out.is_open()) {
                    g_out << now_string() << " [" << m.level << "] " << m.text << '\n';
                    g_out.flush();
                } else {
                    std::fprintf(stderr, "%s [%s] %s\n", now_string().c_str(), m.level.c_str(), m.text.c_str());
                }
                lk.lock();
            }
        }

        // flush remaining
        while (true) {
            std::unique_lock<std::mutex> lk(g_mu);
            if (g_q.empty()) break;
            auto m = std::move(g_q.front());
            g_q.pop();
            lk.unlock();
            if (g_out.is_open()) {
                g_out << now_string() << " [" << m.level << "] " << m.text << '\n';
                g_out.flush();
            } else {
                std::fprintf(stderr, "%s [%s] %s\n", now_string().c_str(), m.level.c_str(), m.text.c_str());
            }
        }
    }

    void enqueue(const char* level, const char* fmt, va_list ap) {
        char buf[2048];
        std::vsnprintf(buf, sizeof(buf), fmt, ap);
        std::unique_lock<std::mutex> lk(g_mu);
        if (g_q.size() >= g_capacity) {
            // 丢弃最旧，避免阻塞
            g_q.pop();
        }
        g_q.push(LogMsg{level, buf});
        lk.unlock();
        g_cv.notify_one();
    }
}

void log_init(const char* file_path, std::size_t queue_capacity) {
    std::lock_guard<std::mutex> lk(g_mu);
    if (g_running.load()) return;
    g_capacity = queue_capacity;
    if (file_path && *file_path) {
        g_out.open(file_path, std::ios::out | std::ios::app);
    }
    g_running.store(true, std::memory_order_release);
    g_worker = std::thread(worker_loop);
}

void log_shutdown() {
    {
        std::lock_guard<std::mutex> lk(g_mu);
        if (!g_running.load()) return;
        g_running.store(false, std::memory_order_release);
    }
    g_cv.notify_all();
    if (g_worker.joinable()) g_worker.join();
    if (g_out.is_open()) g_out.close();
}

void log_info(const char* fmt, ...) {
    va_list ap; va_start(ap, fmt);
    enqueue("INFO", fmt, ap);
    va_end(ap);
}

void log_error(const char* fmt, ...) {
    va_list ap; va_start(ap, fmt);
    enqueue("ERROR", fmt, ap);
    va_end(ap);
}
