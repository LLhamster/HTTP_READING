#ifndef LOGGER_H
#define LOGGER_H

#include <cstddef>

// 初始化异步日志系统。示例：log_init("server.log");
void log_init(const char* file_path, std::size_t queue_capacity = 8192);

// 关闭异步日志系统（刷新缓存并退出后台线程）。
void log_shutdown();

// 信息级别日志，printf 风格格式化。
void log_info(const char* fmt, ...);

// 错误级别日志，printf 风格格式化。
void log_error(const char* fmt, ...);

#endif // LOGGER_H
