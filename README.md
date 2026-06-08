# aifei-cache

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/JDK-1.8+-green.svg)]()
[![Version](https://img.shields.io/badge/version-1.0.0-orange.svg)]()

## 简介

**aifei-cache** 是为 [Aifei](https://gitee.com/gollyhu) 框架设计的缓存插件。只需修改一行配置，即可在 Caffeine、Ehcache、Redisson、Jedis、Lettuce 六种缓存后端之间自由切换，让业务代码完全与底层缓存实现解耦。

## 特性

- **一行配置切换后端** — 修改 `cache.type` 即可从本地缓存切换到分布式缓存
- **静态工具方法** — `CacheUtil.put()` / `CacheUtil.get()` 一行代码完成缓存读写
- **回源获取** — 缓存未命中自动调用数据源，内置穿透/击穿双重保护
- **多实例共存** — 同时使用多个缓存后端（如 Redis 做主缓存，Caffeine 做本地缓存）
- **注解驱动** — `@CachePut` / `@CacheEvict` / `@CachesEvict` 无侵入式缓存
- **Null 值缓存** — 避免每次请求都穿透到数据库
- **多序列化器** — Fastjson2 / Jackson / Hutool5 / JDK 四种序列化方式可选
- **多环境隔离** — namespace 机制自动给 Key 加前缀，轻松区分开发、测试、生产环境

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>cn.hg.aifei</groupId>
    <artifactId>aifei-cache</artifactId>
</dependency>
```

### 2. 配置文件

在 Aifei 配置文件（`app-config.txt`）中添加缓存配置：

```properties
# 主缓存（必须配置 cache.type）
cache.type = caffeine
cache.maxSize = 10000
cache.ttl = 3600
```

> **各后端的最小配置见下方[配置参考](#配置参考)章节。**

### 3. 在应用入口注册并开始使用

```java
// 应用入口 — 注册缓存插件
public class AppConfig implements AifeiConfig<In, Out> {
    public void config(Plugins plugins) {
        plugins.add(new CachePlugin(PropKit.use("app-config.txt")));
    }
}
```

```java
// 业务代码 — 开始缓存
CacheUtil.put("product", "prod-1001", productInfo, 300);
Product p = CacheUtil.get("product", "prod-1001");
```

## 使用指南

### 基本存取

```java
// 存入缓存，指定过期时间 300 秒
CacheUtil.put("product", "prod-1001", productInfo, 300);

// 存入缓存，使用默认过期时间
CacheUtil.put("product", "prod-1002", productInfo);

// 读取缓存
Product p = CacheUtil.get("product", "prod-1001");  // 命中返回对象，未命中返回 null

// 检查键是否存在（区分"值为 null"和"键不存在"，需开启 nullValue）
boolean exists = CacheUtil.exists("product", "prod-1001");

// 删除指定缓存
CacheUtil.evict("product", "prod-1001");

// 一次删除多个缓存
CacheUtil.evict("product", "prod-1001", "prod-1002", "prod-1003");

// 清空某个业务分组（product 下的所有缓存）
CacheUtil.clear("product");
```

### 回源获取（防穿透）

```java
// 缓存有 → 直接返回；缓存无 → 执行 supplier 查询数据库 → 自动写入缓存
User user = CacheUtil.get("user", "uid:" + uid,
    () -> db.findById(uid),       // 回源函数
    600                           // 过期时间（秒）
);

// 简化写法（使用默认过期时间）
User user = CacheUtil.get("user", "uid:" + uid,
    () -> db.findById(uid)
);
```

### 批量操作

```java
// 批量存入
Map<String, Object> data = new HashMap<>();
data.put("prod-1001", product1);
data.put("prod-1002", product2);
data.put("prod-1003", product3);
CacheUtil.putAll("product", data, 3600);

// 批量获取
Set<String> keys = new HashSet<>(Arrays.asList("prod-1001", "prod-1002"));
Map<String, Object> result = CacheUtil.getAll("product", keys);
```

### 多缓存实例

```properties
# 配置两个缓存实例：c1（Jedis）和 local（Caffeine）
cache.names = c1,mem
cache.type = redisson
cache.address = 127.0.0.1:6379

cache.c1.type = jedis
cache.c1.address = 127.0.0.1:6379

cache.mem.type = caffeine
cache.mem.maxSize = 10000
```

```java
// 使用默认缓存实例（主缓存）
CacheUtil.put("product", "key", value);

// 切换到 c1 实例
ICache c1 = CacheUtil.use("c1");
c1.put("order", "key", value);

// 切换到 mem 实例
ICache mem = CacheUtil.use("mem");
mem.put("hot", "key", value);
```

### Null 值缓存

```properties
# 开启 null 值缓存，避免缓存穿透
cache.nullValue = true
```

```java
// 开启后，即使数据源返回 null 也会被缓存，下次查询直接返回 null
// 不会再次穿透到数据库
User user = CacheUtil.get("user", "uid:999",
    () -> {
        // 这个 supplier 可能返回 null（用户不存在）
        return db.findById(999);
    }
);

// 区分"不存在"和"值为 null"
if (CacheUtil.exists("user", "uid:999")) {
    // 键存在（即使值为 null）
} else {
    // 键确实不存在
}
```

### 注解缓存

```java
// 使用前，在路由中注册拦截器
public void config(Routes routes) {
    routes.scan("cn.aifei", new CacheInterceptor());
}
```

```java
// @CachePut：先查缓存 → 没有就执行方法 → 结果自动写入缓存
@CachePut(name = "product", key = "prod-#p(id)", ttlSeconds = 300)
public Product getProduct(Long id) {
    return db.findById(id);  // 缓存没有时才执行
}

// @CacheEvict：方法执行后清除缓存
@CacheEvict(name = "product", key = "prod-#p(id)")
public void updateProduct(Long id, Product product) {
    db.update(product);
}

// 不指定 key → 清除整个 product 分组
@CacheEvict(name = "product")
public void clearProductCache() { }

// @CachesEvict：组合清除多条缓存
@CachesEvict({
    @CacheEvict(name = "product", key = "prod-#p(id)"),
    @CacheEvict(name = "productList", key = "list-#p(categoryId)")
})
public void deleteProduct(Long id, Long categoryId) {
    db.delete(id);
}

// 使用 #p() 表达式动态生成 Key
@CachePut(name = "user", key = "user-#p(id)")
public User getUser(Long id) { ... }

// 指定存入哪个缓存实例
@CachePut(name = "hot", key = "list-#p(categoryId)", cache = "mem")
public List<Product> getHotProducts(String categoryId) { ... }
```

**`#p()` 表达式写法：**

| 写法 | 说明 | 示例 |
|------|------|------|
| `#p(id)` | 按参数名引用 | `key = "prod-#p(id)"` |
| `#p("id")` | 按参数名引用（显式字符串） | `key = "prod-#p(\"id\")"` |
| `#p(0)` | 按参数索引引用（从 0 开始） | `key = "prod-#p(0)"` |
| `#p()` | 拼接所有参数，用 `-` 连接 | `key = "prod-#p()"` |

### 其他实用操作

```java
// 获取当前缓存类型（如 "caffeine"、"redisson"）
String type = CacheUtil.getType();

// 获取当前缓存实例名称
String name = CacheUtil.getName();

// 获取所有已使用的缓存分组名
Set<String> cacheNames = CacheUtil.getCacheNames();

// 获取 namespace
String ns = CacheUtil.getNamespace();

// 修改单个 Key 的过期时间（部分后端实现）
CacheUtil.setTtl("product", "prod-1001", 600);

// 查询剩余过期时间（-1 永久，-2 不存在）
long remaining = CacheUtil.getTtl("product", "prod-1001");

// 获取底层原生缓存实例（逃生口，供特殊场景使用）
CaffeineCache caffeine = CacheUtil.getNativeCache();
```

## 配置参考

### 通用配置（所有后端通用）

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `cache.namespace` | `aifei` | 全局 Key 前缀，用于多环境隔离；设为空字符串关闭 |
| `cache.nullValue` | `false` | 是否缓存 null 值，防止缓存穿透 |
| `cache.ttl` | `3600` | 默认过期时间（秒） |

### 各后端配置

#### Caffeine — 高性能本地缓存（`cache.type = caffeine`）

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `cache.maxSize` | `10000` | 最大缓存条目数 |
| `cache.ttl` | `3600` | 默认过期时间（秒） |

#### Local — 线程安全的本地缓存（`cache.type = local`）

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `cache.ttl` | `3600` | 默认过期时间（秒），设为 `0` 表示永不过期 |

#### Ehcache — 支持堆外存储（`cache.type = ehcache`）

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `cache.heapEntries` | `10000` | 堆内最大条目数 |
| `cache.ttl` | `3600` | 默认过期时间（秒） |
| `cache.offHeapMB` | `0` | 堆外内存大小（MB），`0` 表示不启用 |

#### Redisson — Redis 分布式缓存（`cache.type = redisson`）

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `cache.address` | `127.0.0.1:6379` | Redis 地址 |
| `cache.password` | - | Redis 密码（可选） |
| `cache.database` | `0` | 数据库编号 |
| `cache.ttl` | `3600` | 默认过期时间（秒） |
| `cache.redissonJson` | - | 自定义 Redisson JSON 配置路径（可选） |

#### Jedis — Redis 分布式缓存（`cache.type = jedis`）

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `cache.address` | `127.0.0.1:6379` | Redis 地址 |
| `cache.password` | - | Redis 密码（可选） |
| `cache.database` | `0` | 数据库编号 |
| `cache.ttl` | `3600` | 默认过期时间（秒） |
| `cache.serializer` | `jdk` | 序列化器，可选 `jdk` / `fastjson2` / `jackson` / `hutool5` |
| `cache.maxTotal` | `8` | 连接池最大连接数 |
| `cache.maxIdle` | `8` | 连接池最大空闲连接数 |
| `cache.minIdle` | `0` | 连接池最小空闲连接数 |

#### Lettuce — Redis 分布式缓存（`cache.type = lettuce`）

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `cache.address` | `127.0.0.1:6379` | Redis 地址 |
| `cache.password` | - | Redis 密码（可选） |
| `cache.database` | `0` | 数据库编号 |
| `cache.ttl` | `3600` | 默认过期时间（秒） |

### 配置规则速查

| 规则 | 说明 |
|------|------|
| 主缓存必填 | `cache.type` 缺失时启动报错 |
| 扩展缓存声明 | 通过 `cache.names` 逗号分隔，如 `cache.names = c1,c2` |
| 扩展缓存前缀 | 使用 `cache.<name>.`，如 `cache.c1.type` |
| 保留名称 | `main` 是系统保留名称，扩展缓存不能使用 |
| 扩展缓存选填 | 未配置 `cache.<name>.type` 的扩展缓存会被跳过 |

### 完整配置示例

```properties
# ──────────────── 缓存配置 ────────────────
# 扩展缓存实例名称列表
cache.names = c1,mem

# ── 主缓存：Redisson ──
cache.type = redisson
cache.address = 127.0.0.1:6379
cache.password =
cache.database = 0
cache.ttl = 3600
cache.nullValue = true
cache.namespace = aifei

# ── c1 缓存：Jedis ──
cache.c1.type = jedis
cache.c1.address = 127.0.0.1:6379
cache.c1.database = 1
cache.c1.ttl = 7200
cache.c1.serializer = fastjson2

# ── mem 缓存：Ehcache ──
cache.mem.type = ehcache
cache.mem.heapEntries = 10000
cache.mem.ttl = 0
```

## API 速查

### 数据操作（CacheUtil 静态方法）

| 方法 | 说明 |
|------|------|
| `put(cacheName, key, value)` | 存入缓存，使用默认 TTL |
| `put(cacheName, key, value, ttlSeconds)` | 存入缓存，指定 TTL（秒） |
| `<T> T get(cacheName, key)` | 获取缓存值，不存在返回 null |
| `<T> T get(cacheName, key, supplier)` | 回源获取，使用默认 TTL |
| `<T> T get(cacheName, key, supplier, ttlSeconds)` | 回源获取，指定 TTL |
| `putAll(cacheName, map, ttlSeconds)` | 批量存入 |
| `getAll(cacheName, keys)` | 批量获取 |
| `exists(cacheName, key)` | 检查键是否存在 |
| `evict(cacheName, keys...)` | 删除一个或多个缓存键 |
| `clear(cacheNames...)` | 清空指定缓存分组 |
| `clearAll()` | 清空当前实例所有数据 |

### 实例与元数据

| 方法 | 说明 |
|------|------|
| `CacheUtil.use(name)` | 切换到指定缓存实例 |
| `getName()` | 获取当前缓存实例名称 |
| `getType()` | 获取当前缓存类型 |
| `getCacheNames()` | 获取所有已记录的缓存分组名 |
| `getNamespace()` | 获取命名空间 |
| `getDefaultTtl()` | 获取默认 TTL |
| `setDefaultTtl(ttlSeconds)` | 修改默认 TTL |
| `setTtl(cacheName, key, ttlSeconds)` | 设置 Key 级别 TTL |
| `getTtl(cacheName, key)` | 获取 Key 剩余 TTL（-1 永久，-2 不存在） |
| `<T> T getNativeCache()` | 获取底层原生缓存实例 |

### 注解

| 注解 | 说明 |
|------|------|
| `@CachePut` | 方法执行前查缓存，命中则返回；未命中则执行方法并写入缓存 |
| `@CacheEvict` | 方法执行后清除指定的 Key 或整个缓存分组 |
| `@CachesEvict` | 组合注解，一条方法上配置多条 `@CacheEvict` 规则 |

## 常见问题

**Q: 启动报错"主缓存配置缺失：必须配置 cache.type"？**

A: 必须在配置文件中设置 `cache.type`，这是必填项。选择一个后端填入即可，如 `cache.type = caffeine`。

**Q: 如何从开发环境切换到生产环境？**

A: 默认 `cache.namespace = aifei` 会自动给所有 Key 加前缀隔离。不同环境使用不同的 namespace 即可避免 Key 冲突：

```properties
# 开发环境
cache.namespace = dev

# 生产环境
cache.namespace = prod
```

**Q: 注解缓存不生效？**

A: 使用注解缓存需要同时注册 CacheInterceptor：

```java
public void config(Routes routes) {
    routes.scan("your.package", new CacheInterceptor());
}
```

**Q: 缓存穿透和击穿有什么区别，如何防护？**

A: aifei-cache 已内置双重防护：
- **穿透防护**：开启 `cache.nullValue = true`，数据库查不到的也会缓存
- **击穿防护**：使用回源方法 `get(cacheName, key, supplier, ttl)`，分布式模式自动加锁，保证同一时间只有一个请求去查库

**Q: 如何同时使用本地缓存和 Redis 缓存？**

A: 通过多缓存实例实现，主缓存用 Redis，附加一个本地缓存做热点加速：

```properties
cache.names = local
cache.type = redisson
cache.address = 127.0.0.1:6379

cache.local.type = caffeine
cache.local.maxSize = 1000
cache.local.ttl = 60
```

```java
// 热点数据放本地缓存
CacheUtil.use("local").get("hot", "rank",
    () -> CacheUtil.get("rank", "top100", () -> db.getTop100(), 3600),
    60
);
```

**Q: 存储键的格式是什么？**

A: 默认格式为 `namespace:cacheName:key`。例如配置 `cache.namespace = aifei` 时，`CacheUtil.put("product", "prod-1001", data)` 实际存储键为 `aifei:product:prod-1001`。如果不需要 namespace 前缀，将 `cache.namespace` 设为空字符串即可。

## 许可证

[Apache License 2.0](LICENSE)
