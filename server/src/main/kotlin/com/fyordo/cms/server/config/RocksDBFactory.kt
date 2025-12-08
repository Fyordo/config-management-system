package com.fyordo.cms.server.config

import com.fyordo.cms.server.config.props.RocksDbConfig
import org.rocksdb.CompressionType
import org.rocksdb.Options
import org.rocksdb.RocksDB
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.File

@Configuration
class RocksDBFactory {
    @Bean(destroyMethod = "close")
    fun rocksDB(rocksDbConfig: RocksDbConfig): RocksDB {
        RocksDB.loadLibrary()

        val options = Options()
            .setCreateIfMissing(true)
            .setMaxBackgroundJobs(rocksDbConfig.maxBackgroundJobs)
            .setMaxOpenFiles(rocksDbConfig.maxOpenFiles)

        if (rocksDbConfig.compressionEnabled) {
            options.setCompressionType(CompressionType.LZ4_COMPRESSION)
        }

        val dbDir = File(rocksDbConfig.path)

        if (!dbDir.exists()) {
            dbDir.mkdirs()
        }
        
        return RocksDB.open(options, dbDir.absolutePath)
    }
}
