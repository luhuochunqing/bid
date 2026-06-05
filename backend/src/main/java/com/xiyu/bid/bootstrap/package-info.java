/**
 * Bootstrap — startup-time initialization that sits outside config/ to avoid
 * ArchitectureTest RULE 9 (config must not depend on service/repository).
 * Classes here are free to inject Repository beans directly.
 */
package com.xiyu.bid.bootstrap;
