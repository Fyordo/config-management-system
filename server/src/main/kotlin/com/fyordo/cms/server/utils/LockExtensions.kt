package com.fyordo.cms.server.utils

import java.util.concurrent.locks.ReadWriteLock

inline fun <T> ReadWriteLock.read(action: () -> T): T {
    readLock().lock()
    return try {
        action()
    } finally {
        readLock().unlock()
    }
}

inline fun <T> ReadWriteLock.write(action: () -> T): T {
    writeLock().lock()
    return try {
        action()
    } finally {
        writeLock().unlock()
    }
}
