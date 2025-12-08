package com.fyordo.cms.server.config

import com.fyordo.cms.server.config.props.RocksDbConfig
import mu.KotlinLogging
import org.rocksdb.CompressionType
import org.rocksdb.Options
import org.rocksdb.RocksDB
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.File

private val logger = KotlinLogging.logger {}

@Configuration
class RocksDBFactory {
    @Bean(destroyMethod = "close")
    fun rocksDB(rocksDbConfig: RocksDbConfig): RocksDB {
        logger.info { "RocksDB initializing..." }
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
            logger.info { "Creating dir ${dbDir.absolutePath} for RocksDB" }
            dbDir.mkdirs()
        }

        try {
            return RocksDB.open(options, dbDir.absolutePath).apply {
                logger.info { "RocksDB initialized successfully" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to initialize RocksDB" }
            throw IllegalStateException(e)
        }
    }
}
