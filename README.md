# aifei-cache

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/JDK-1.8+-green.svg)]()
[![Version](https://img.shields.io/badge/version-1.0.0-orange.svg)]()

## 简介

**aifei-cache** 是基于 [Aifei](https://gitee.com/gollyhu) 框架的缓存插件模块，提供统一的缓存抽象层，支持多种缓存后端实现，让业务代码与缓存具体实现解耦。

## 特性

- **统一 API**：`ICache` 接口定义 `put` / `get` / `evict` / `clear` 等原子操作
- **缓存分组**：通过 `cacheName` 参数实现多业务数据隔离，namespace 全局前缀防冲突
- **多后端支持**：Caffeine / Ehcache / Local / Redisson / Jedis / Lettuce 六种实现
- **回源保护**：`get(cacheName, key, supplier)` 缓存穿透保护；分布式实现内置锁防击穿
- **null 值缓存**：可选 `nullValue` 模式，区分"键不存在"和"值为 null"
- **注解驱动**：`@CachePut` / `@CacheEvict` / `@CachesEvict` 无侵入缓存
- **多序列化器**：Fastjson2 / Jackson / Hutool5 / Hutool6 / JDK 内置序列化
- **配置驱动**：通过 Aifei Plugin 机制从配置文件加载缓存实例，零代码切换后端

## 依赖

### 编译期依赖

| 依赖 | 版本 | 说明 |
|------|------|------|
| [Aifei](https://gitee.com/gollyhu) | 1.0.1 | Aifei 框架核心 |
| aifei-log | 1.0.1 | Aifei 日志模块 |
| aifei-enjoy | 1.0.1 | Enjoy 模板引擎（缓存 Key 表达式） |
| [Caffeine](https://github.com/ben-manes/caffeine) | 2.9.3 | 高性能本地缓存库 |
| [Ehcache](https://www.ehcache.org/) | 3.8.1 | JSR-107 兼容本地缓存 |
| [Redisson](https://github.com/redisson/redisson) | 4.4.0 | Redis 分布式缓存客户端 |
| [Jedis](https://github.com/redis/jedis) | 7.5.0 | Redis Java 客户端 |
| [Lettuce](https://lettuce.io/) | 7.5.2 | 同步/异步/响应式 Redis 客户端 |
| [Fastjson2](https://github.com/alibaba/fastjson2) | 2.0.62 | JSON 序列化（带类型信息） |
| [Jackson](https://github.com/FasterXML/jackson) | 2.21.3 | JSON 序列化（带类型信息） |
| [Hutool-json](https://hutool.cn/) | 5.8.38 | JSON 序列化（5.x 版本） |
| [Hutool-json v6](https://hutool.cn/) | 6.0.0-M22 | JSON 序列化（6.x 版本） |

### 测试期依赖

| 依赖 | 版本 | 说明 |
|------|------|------|
| JUnit | 4.13.2 | 单元测试框架 |
| SLF4J Simple | 2.0.18 | 测试日志实现 |

## 架构

```
ICache (接口)
├── AbstractCache                         # 模板基类：key 校验 + 存储键生成 + NullValue 哨兵
│   ├── CaffeineCache                     # 基于 Caffeine，per-entry TTL + 统计信息
│   ├── LocalCache                        # 基于 ConcurrentHashMap，惰性删除 + 后台清理
│   ├── EhcacheCache                      # 基于 Ehcache 3.x，堆内/堆外/磁盘多级存储
│   └── AbstractDistributedCache          # 分布式基类：异常包装 + 分布式锁防击穿
│       ├── RedissonCache                 # 基于 Redisson RMapCache
│       ├── JedisCache                    # 基于 Jedis RedisClient + 连接池
│       └── LettuceCache                  # 基于 Lettuce RedisClient + SCAN 批量删除
```

### 核心组件

| 组件 | 职责 |
|------|------|
| `ICache` | 核心接口，定义 `put` / `get` / `evict` / `clear` 等原子操作 |
| `CachePlugin` | Aifei Plugin 实现，启动时从配置加载主缓存和扩展缓存 |
| `CacheUtil` | 静态工具类，便捷访问缓存 |

### 注解缓存

| 注解 | 说明 |
|------|------|
| `@CachePut` | 方法执行前查缓存，命中返回；未命中执行方法并写入缓存 |
| `@CacheEvict` | 方法执行后清除指定 Key 或整个缓存分组 |
| `@CachesEvict` | 组合注解，支持多条 `@CacheEvict` 规则 |
| `CacheInterceptor` | AOP 拦截器，解析注解并自动处理缓存读写 |

## 快速开始

### 1. 添加 Maven 依赖

```xml
<dependency>
    <groupId>cn.hg.aifei</groupId>
    <artifactId>aifei-cache</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置缓存

在 Aifei 配置文件（`app-config.txt`）中添加：

```properties
# ──────────────── 缓存配置 ────────────────
# 扩展缓存实例名称列表（逗号分隔）
cache.names = c1,local

# 【主缓存，必须配置 - Redisson】
cache.type = redisson
cache.address = 127.0.0.1:6379
cache.password =
cache.database = 0
cache.ttl = 3600
cache.nullValue = true

# 【c1 缓存 - Jedis】
cache.c1.type = jedis
cache.c1.address = 127.0.0.1:6379
cache.c1.database = 1
cache.c1.ttl = 7200

# 【local 缓存 - ConcurrentHashMap】
cache.local.type = local
cache.local.ttl = 0
```

### 3. 使用缓存

```java
// 方式一：CacheUtil 静态方法（使用默认缓存实例）
CacheUtil.put("product", "prod-1001", productInfo, 300);
Product p = CacheUtil.get("product", "prod-1001");

// 方式二：指定缓存实例
ICache cache = CacheUtil.use("c1");
cache.put("order", "order-2001", orderInfo);

// 方式三：回源获取（缓存穿透保护）
User user = CacheUtil.get("user", "uid:" + uid,
    () -> db.findById(uid), 600);

// 方式四：注解缓存（需配合 @Before(CacheInterceptor.class)）
@CachePut(name = "product", key = "prod-#para(id)", ttlSeconds = 300)
public Product getProduct(Long id) { ... }
```

## API 参考

### ICache、CacheUtil 核心方法

| 方法 | 说明 |
|------|------|
| `put(cacheName, key, value)` | 存入缓存，使用默认 TTL |
| `put(cacheName, key, value, ttlSeconds)` | 存入缓存，指定 TTL（秒） |
| `<T> T get(cacheName, key)` | 获取缓存值，不存在返回 null |
| `<T> T get(cacheName, key, supplier)` | 回源获取，使用默认 TTL |
| `<T> T get(cacheName, key, supplier, ttlSeconds)` | 回源获取，指定 TTL |
| `exists(cacheName, key)` | 检查键是否存在（区别于值为 null） |
| `evict(cacheName, keys...)` | 删除一个或多个缓存键 |
| `clear(cacheNames...)` | 清除指定缓存分组下所有条目 |
| `clearAll()` | 清空当前缓存实例所有数据 |
| `putAll(cacheName, map, ttlSeconds)` | 批量存入 |
| `getAll(cacheName, keys)` | 批量获取 |
| `<T> T getNativeCache()` | 获取底层原生缓存实例（逃生口） |
| `setTtl(cacheName, key, ttlSeconds)` | 设置 Key 级别 TTL（可选实现） |
| `getTtl(cacheName, key)` | 获取 Key 剩余 TTL（-1 永久，-2 不存在） |
| `getCacheNames()` | 获取所有已记录的 cacheName 集合 |
| `getNamespace()` | 获取命名空间 |
| use("name") | CacheUtil 专用，指定缓存实例 |

### 存储键规则

```
namespace:cacheName:key    # namespace 非空时
cacheName:key              # namespace 为空时
```

### 各 Provider 配置参数

| Provider | 配置项 | 默认值 | 说明 |
|----------|--------|--------|------|
| **caffeine** | `maxSize` | 10000 | 最大缓存条目数 |
| | `ttl` | 3600 | 默认过期时间（秒） |
| **local** | `ttl` | 3600 | 默认过期时间（秒），0 = 永不过期 |
| **ehcache** | `heapEntries` | 10000 | 堆内最大条目数 |
| | `ttl` | 3600 | 默认过期时间（秒） |
| | `offHeapMB` | 0 | 堆外内存大小（MB），0 不启用 |
| **redisson** | `address` | 127.0.0.1:6379 | Redis 地址 |
| | `password` | - | Redis 密码 |
| | `database` | 0 | 数据库编号 |
| | `ttl` | 3600 | 默认过期时间（秒） |
| | `redissonJson` | - | 自定义 Redisson JSON 配置路径 |
| **jedis** | `address` | 127.0.0.1:6379 | Redis 地址 |
| | `password` | - | Redis 密码 |
| | `database` | 0 | 数据库编号 |
| | `ttl` | 3600 | 默认过期时间（秒） |
| | `maxTotal` | 8 | 连接池最大连接数 |
| | `maxIdle` | 8 | 连接池最大空闲连接数 |
| | `minIdle` | 0 | 连接池最小空闲连接数 |
| **lettuce** | `address` | 127.0.0.1:6379 | Redis 地址 |
| | `password` | - | Redis 密码 |
| | `database` | 0 | 数据库编号 |
| | `ttl` | 3600 | 默认过期时间（秒） |

### 通用配置项

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `namespace` | 全局 Key 前缀，用于多环境隔离 | `aifei`（显式设为空字符串关闭） |
| `nullValue` | 是否允许缓存 null 值 | `false` |
| `provider` | 自定义 Provider 全限定类名 | 按 type 自动匹配 |

> **配置前缀说明**：主缓存使用 `cache.` 前缀；扩展缓存使用 `cache.<name>.` 前缀。

## 配置规范

| 规则 | 说明 |
|------|------|
| 主缓存必填 | `cache.type` 缺失则启动抛出 `CacheException` |
| 扩展缓存声明 | 通过 `cache.names` 逗号分隔，如 `cache.names = c1,c2` |
| 保留名称 | `main` 为系统保留的主缓存名称，扩展缓存不得使用 |
| 默认 Provider 映射 | caffeine / local / ehcache / redisson / jedis / lettuce → 对应 `*CacheProvider` |
| 类型缺失跳过 | 扩展缓存未配置 type 时跳过并记录警告 |

## 目录结构

```
src/main/java/cn/hg/aifei/cache/
├── api/                             # 核心接口定义
│   ├── ICache.java                  #   缓存接口
│   ├── ICacheProvider.java          #   缓存工厂接口
│   ├── AbstractCache.java           #   模板方法基类
│   ├── NullValue.java               #   Null 值哨兵
│   ├── CacheException.java          #   异常基类
│   ├── CacheConnectionException.java
│   ├── CacheOperationException.java
│   └── CacheSerializationException.java
├── annotation/                      # 缓存注解
│   ├── CachePut.java                #   读/写缓存注解
│   ├── CacheEvict.java              #   清除缓存注解
│   └── CachesEvict.java             #   组合清除注解
├── core/                            # 核心管理器
│   ├── CacheManager.java            #   缓存单例管理器
│   └── CacheUtil.java               #   缓存工具类
├── interceptor/                     # AOP 拦截器
│   ├── CacheInterceptor.java        #   缓存注解拦截器
│   ├── KeyGenerator.java            #   Key 表达式生成器
│   ├── KeyContext.java              #   线程上下文
│   └── directive/
│       └── ParaDirective.java       #   #para() 模板指令
├── plugin/
│   └── CachePlugin.java             #   框架插件入口
├── serializer/                      # 序列化器
│   ├── ICacheSerializer.java        #   序列化接口
│   ├── Fastjson2ICacheSerializer.java
│   ├── JacksonCacheSerializer.java
│   ├── Hutool5CacheSerializer.java
│   ├── Hutool6CacheSerializer.java
│   └── JdkICacheSerializer.java
└── impl/                            # 缓存实现
    ├── local/                       #   本地缓存
    │   ├── CaffeineCache.java
    │   ├── CaffeineCacheProvider.java
    │   ├── LocalCache.java
    │   ├── LocalCacheProvider.java
    │   ├── EhcacheCache.java
    │   └── EhcacheCacheProvider.java
    └── distribute/                  #   分布式缓存
        ├── AbstractDistributedCache.java
        └── redis/
            ├── RedissonCache.java
            ├── RedissonCacheProvider.java
            ├── JedisCache.java
            ├── JedisCacheProvider.java
            ├── LettuceCache.java
            └── LettuceCacheProvider.java
```

## 常用命令

```bash
mvn compile                              # 编译项目
mvn test-compile                         # 编译测试代码
mvn test                                 # 运行全部测试
mvn test -Dtest=CaffeineCacheTest        # 运行指定测试
mvn package                              # 打包
```

## 开源协议

[Apache License 2.0](LICENSE)
