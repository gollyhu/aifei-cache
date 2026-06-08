# aifei-cache

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/JDK-1.8+-green.svg)]()
[![Version](https://img.shields.io/badge/version-1.0.0-orange.svg)]()

## Overview

**aifei-cache** is a caching plugin for the [Aifei](https://gitee.com/gollyhu) framework. Switch between six cache backends—Caffeine, Ehcache, Redisson, Jedis, and Lettuce—by changing a single configuration line. Your business code stays completely decoupled from the underlying cache implementation.

## Features

- **One-line backend switch** — Change `cache.type` to move from local to distributed caching
- **Static utility methods** — `CacheUtil.put()` / `CacheUtil.get()` handle caching in a single line
- **Read-through support** — Auto-load from data source on cache miss, with built-in penetration and hot-key protection
- **Multiple instances** — Use multiple cache backends simultaneously (e.g., Redis as primary, Caffeine for local hot data)
- **Annotation-driven** — `@CachePut` / `@CacheEvict` / `@CachesEvict` for non-invasive caching
- **Null value caching** — Avoid repeated database hits for non-existent records
- **Multiple serializers** — Fastjson2 / Jackson / Hutool5 / JDK serialization options
- **Environment isolation** — Namespace-based key prefixing for dev, test, and production environments

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>cn.hg.aifei</groupId>
    <artifactId>aifei-cache</artifactId>
</dependency>
```

### 2. Configuration

Add cache settings to your Aifei config file (`app-config.txt`):

```properties
# Main cache (cache.type is required)
cache.type = caffeine
cache.maxSize = 10000
cache.ttl = 3600
```

> **See the [Configuration Reference](#configuration-reference) below for minimal configs for each backend.**

### 3. Register the Plugin and Start Caching

```java
// Application entry — register the cache plugin
public class AppConfig implements AifeiConfig<In, Out> {
    public void config(Plugins plugins) {
        plugins.add(new CachePlugin(PropKit.use("app-config.txt")));
    }
}
```

```java
// Business code — start caching
CacheUtil.put("product", "prod-1001", productInfo, 300);
Product p = CacheUtil.get("product", "prod-1001");
```

## Usage Guide

### Basic Operations

```java
// Put with custom TTL (300 seconds)
CacheUtil.put("product", "prod-1001", productInfo, 300);

// Put with default TTL
CacheUtil.put("product", "prod-1002", productInfo);

// Get
Product p = CacheUtil.get("product", "prod-1001");  // Returns null if not found

// Check if key exists (distinguishes "null value" from "key not found"; requires nullValue enabled)
boolean exists = CacheUtil.exists("product", "prod-1001");

// Delete specific keys
CacheUtil.evict("product", "prod-1001");

// Delete multiple keys at once
CacheUtil.evict("product", "prod-1001", "prod-1002", "prod-1003");

// Clear an entire cache group (all entries under "product")
CacheUtil.clear("product");
```

### Read-Through (Cache-Aside Pattern)

```java
// Cache hit → return immediately; Cache miss → execute supplier → auto-write to cache
User user = CacheUtil.get("user", "uid:" + uid,
    () -> db.findById(uid),       // fallback function
    600                           // TTL in seconds
);

// Simplified version (uses default TTL)
User user = CacheUtil.get("user", "uid:" + uid,
    () -> db.findById(uid)
);
```

### Batch Operations

```java
// Batch put
Map<String, Object> data = new HashMap<>();
data.put("prod-1001", product1);
data.put("prod-1002", product2);
data.put("prod-1003", product3);
CacheUtil.putAll("product", data, 3600);

// Batch get
Set<String> keys = new HashSet<>(Arrays.asList("prod-1001", "prod-1002"));
Map<String, Object> result = CacheUtil.getAll("product", keys);
```

### Multiple Cache Instances

```properties
# Define two cache instances: c1 (Jedis) and mem (Caffeine)
cache.names = c1,mem
cache.type = redisson
cache.address = 127.0.0.1:6379

cache.c1.type = jedis
cache.c1.address = 127.0.0.1:6379

cache.mem.type = caffeine
cache.mem.maxSize = 10000
```

```java
// Use default cache instance (main)
CacheUtil.put("product", "key", value);

// Switch to c1 instance
ICache c1 = CacheUtil.use("c1");
c1.put("order", "key", value);

// Switch to mem instance
ICache mem = CacheUtil.use("mem");
mem.put("hot", "key", value);
```

### Null Value Caching

```properties
# Enable null value caching to prevent cache penetration
cache.nullValue = true
```

```java
// With nullValue enabled, even null results from the supplier are cached
// Subsequent requests return null directly without hitting the database
User user = CacheUtil.get("user", "uid:999",
    () -> {
        // This supplier may return null (user does not exist)
        return db.findById(999);
    }
);

// Distinguish "key not found" from "value is null"
if (CacheUtil.exists("user", "uid:999")) {
    // Key exists (even if value is null)
} else {
    // Key genuinely does not exist
}
```

### Annotation-Based Caching

```java
// Register the interceptor in your route configuration
public void config(Routes routes) {
    routes.scan("cn.aifei", new CacheInterceptor());
}
```

```java
// @CachePut: check cache first → execute method on miss → auto-write result to cache
@CachePut(name = "product", key = "prod-#p(id)", ttlSeconds = 300)
public Product getProduct(Long id) {
    return db.findById(id);  // Only executed on cache miss
}

// @CacheEvict: evict cache after method execution
@CacheEvict(name = "product", key = "prod-#p(id)")
public void updateProduct(Long id, Product product) {
    db.update(product);
}

// Without key → clears the entire cache group
@CacheEvict(name = "product")
public void clearProductCache() { }

// @CachesEvict: combine multiple eviction rules
@CachesEvict({
    @CacheEvict(name = "product", key = "prod-#p(id)"),
    @CacheEvict(name = "productList", key = "list-#p(categoryId)")
})
public void deleteProduct(Long id, Long categoryId) {
    db.delete(id);
}

// #p() expressions for dynamic key generation
@CachePut(name = "user", key = "user-#p(id)")
public User getUser(Long id) { ... }

// Specify which cache instance to use
@CachePut(name = "hot", key = "list-#p(categoryId)", cache = "mem")
public List<Product> getHotProducts(String categoryId) { ... }
```

**`#p()` expression reference:**

| Syntax | Description | Example |
|--------|-------------|---------|
| `#p(id)` | By parameter name | `key = "prod-#p(id)"` |
| `#p("id")` | By parameter name (explicit string) | `key = "prod-#p(\"id\")"` |
| `#p(0)` | By parameter index (0-based) | `key = "prod-#p(0)"` |
| `#p()` | All parameters joined by `-` | `key = "prod-#p()"` |

### Other Utilities

```java
// Get current cache type (e.g., "caffeine", "redisson")
String type = CacheUtil.getType();

// Get current cache instance name
String name = CacheUtil.getName();

// Get all recorded cache group names
Set<String> cacheNames = CacheUtil.getCacheNames();

// Get namespace
String ns = CacheUtil.getNamespace();

// Update TTL for a single key (supported by some backends)
CacheUtil.setTtl("product", "prod-1001", 600);

// Get remaining TTL (-1 = permanent, -2 = does not exist)
long remaining = CacheUtil.getTtl("product", "prod-1001");

// Access the underlying native cache instance (escape hatch)
CaffeineCache caffeine = CacheUtil.getNativeCache();
```

## Configuration Reference

### Common Settings (All Backends)

| Setting | Default | Description |
|---------|---------|-------------|
| `cache.namespace` | `aifei` | Global key prefix for multi-environment isolation; set to empty string to disable |
| `cache.nullValue` | `false` | Enable null value caching to prevent cache penetration |
| `cache.ttl` | `3600` | Default TTL in seconds |

### Per-Backend Settings

#### Caffeine — High-Performance Local Cache (`cache.type = caffeine`)

| Setting | Default | Description |
|---------|---------|-------------|
| `cache.maxSize` | `10000` | Maximum cache entries |
| `cache.ttl` | `3600` | Default TTL in seconds |

#### Local — Thread-Safe Local Cache (`cache.type = local`)

| Setting | Default | Description |
|---------|---------|-------------|
| `cache.ttl` | `3600` | Default TTL in seconds; set to `0` for no expiration |

#### Ehcache — Off-Heap Storage Support (`cache.type = ehcache`)

| Setting | Default | Description |
|---------|---------|-------------|
| `cache.heapEntries` | `10000` | Maximum on-heap entries |
| `cache.ttl` | `3600` | Default TTL in seconds |
| `cache.offHeapMB` | `0` | Off-heap memory size in MB; `0` to disable |

#### Redisson — Redis Distributed Cache (`cache.type = redisson`)

| Setting | Default | Description |
|---------|---------|-------------|
| `cache.address` | `127.0.0.1:6379` | Redis address |
| `cache.password` | - | Redis password (optional) |
| `cache.database` | `0` | Database number |
| `cache.ttl` | `3600` | Default TTL in seconds |
| `cache.redissonJson` | - | Custom Redisson JSON config path (optional) |

#### Jedis — Redis Distributed Cache (`cache.type = jedis`)

| Setting | Default | Description |
|---------|---------|-------------|
| `cache.address` | `127.0.0.1:6379` | Redis address |
| `cache.password` | - | Redis password (optional) |
| `cache.database` | `0` | Database number |
| `cache.ttl` | `3600` | Default TTL in seconds |
| `cache.serializer` | `jdk` | Serializer: `jdk` / `fastjson2` / `jackson` / `hutool5` |
| `cache.maxTotal` | `8` | Connection pool max connections |
| `cache.maxIdle` | `8` | Connection pool max idle connections |
| `cache.minIdle` | `0` | Connection pool min idle connections |

#### Lettuce — Redis Distributed Cache (`cache.type = lettuce`)

| Setting | Default | Description |
|---------|---------|-------------|
| `cache.address` | `127.0.0.1:6379` | Redis address |
| `cache.password` | - | Redis password (optional) |
| `cache.database` | `0` | Database number |
| `cache.ttl` | `3600` | Default TTL in seconds |

### Configuration Rules at a Glance

| Rule | Description |
|------|-------------|
| Main cache required | Missing `cache.type` causes startup error |
| Extension cache declaration | Comma-separated list via `cache.names`, e.g., `cache.names = c1,c2` |
| Extension cache prefix | Use `cache.<name>.`, e.g., `cache.c1.type` |
| Reserved name | `main` is reserved for the primary cache; do not use for extensions |
| Extension cache optional | Extensions without `cache.<name>.type` are silently skipped |

### Full Configuration Example

```properties
# ──────────────── Cache Configuration ────────────────
# Extension cache instance names
cache.names = c1,mem

# ── Main cache: Redisson ──
cache.type = redisson
cache.address = 127.0.0.1:6379
cache.password =
cache.database = 0
cache.ttl = 3600
cache.nullValue = true
cache.namespace = aifei

# ── c1 cache: Jedis ──
cache.c1.type = jedis
cache.c1.address = 127.0.0.1:6379
cache.c1.database = 1
cache.c1.ttl = 7200
cache.c1.serializer = fastjson2

# ── mem cache: Ehcache ──
cache.mem.type = ehcache
cache.mem.heapEntries = 10000
cache.mem.ttl = 0
```

## API Quick Reference

### Data Operations (CacheUtil Static Methods)

| Method | Description |
|--------|-------------|
| `put(cacheName, key, value)` | Put into cache with default TTL |
| `put(cacheName, key, value, ttlSeconds)` | Put into cache with custom TTL (seconds) |
| `<T> T get(cacheName, key)` | Get cached value; returns null if not found |
| `<T> T get(cacheName, key, supplier)` | Read-through with default TTL |
| `<T> T get(cacheName, key, supplier, ttlSeconds)` | Read-through with custom TTL |
| `putAll(cacheName, map, ttlSeconds)` | Batch put |
| `getAll(cacheName, keys)` | Batch get |
| `exists(cacheName, key)` | Check if key exists |
| `evict(cacheName, keys...)` | Delete one or more keys |
| `clear(cacheNames...)` | Clear specified cache group(s) |
| `clearAll()` | Clear all data in current instance |

### Instance & Metadata

| Method | Description |
|--------|-------------|
| `CacheUtil.use(name)` | Switch to a named cache instance |
| `getName()` | Get current cache instance name |
| `getType()` | Get current cache type |
| `getCacheNames()` | Get all recorded cache group names |
| `getNamespace()` | Get namespace |
| `getDefaultTtl()` | Get default TTL |
| `setDefaultTtl(ttlSeconds)` | Update default TTL |
| `setTtl(cacheName, key, ttlSeconds)` | Set key-level TTL |
| `getTtl(cacheName, key)` | Get remaining TTL (-1 = permanent, -2 = not found) |
| `<T> T getNativeCache()` | Access underlying native cache instance |

### Annotations

| Annotation | Description |
|------------|-------------|
| `@CachePut` | Check cache before method; write result to cache on miss |
| `@CacheEvict` | Evict specified keys or entire cache group after method execution |
| `@CachesEvict` | Combine multiple `@CacheEvict` rules on a single method |

## FAQ

**Q: Startup error "主缓存配置缺失：必须配置 cache.type"?**

A: You must set `cache.type` in your configuration file. Choose any backend, e.g., `cache.type = caffeine`.

**Q: How do I switch from dev to production?**

A: Use different `cache.namespace` values to isolate keys across environments:

```properties
# Development
cache.namespace = dev

# Production
cache.namespace = prod
```

**Q: Annotation-based caching is not working?**

A: Make sure you've registered the `CacheInterceptor`:

```java
public void config(Routes routes) {
    routes.scan("your.package", new CacheInterceptor());
}
```

**Q: What's the difference between cache penetration and hot-key invalidations, and how are they handled?**

A: aifei-cache provides built-in dual protection:
- **Penetration**: Enable `cache.nullValue = true` to cache null results, preventing repeated database hits for non-existent data
- **Hot-key invalidation**: Use `get(cacheName, key, supplier, ttl)` for read-through—in distributed mode, a distributed lock ensures only one request queries the database at a time

**Q: Can I use both a local cache and Redis at the same time?**

A: Yes, via multiple cache instances. Use Redis as the main cache and add a local cache for hot data:

```properties
cache.names = local
cache.type = redisson
cache.address = 127.0.0.1:6379

cache.local.type = caffeine
cache.local.maxSize = 1000
cache.local.ttl = 60
```

```java
// Hot data in local cache, fallback to Redis then database
CacheUtil.use("local").get("hot", "rank",
    () -> CacheUtil.get("rank", "top100", () -> db.getTop100(), 3600),
    60
);
```

**Q: What format does the storage key use?**

A: Default format is `namespace:cacheName:key`. For example, with `cache.namespace = aifei`, calling `CacheUtil.put("product", "prod-1001", data)` stores under the key `aifei:product:prod-1001`. To disable the namespace prefix, set `cache.namespace` to an empty string.

## License

[Apache License 2.0](LICENSE)
