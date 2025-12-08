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

        initDir(dbDir)

        checkDirRights(dbDir)

        return try {
            RocksDB.open(options, dbDir.absolutePath).apply {
                logger.info { "RocksDB initialized successfully at ${dbDir.absolutePath}" }
            }
        } catch (e: Exception) {
            options.close()
            logger.error(e) { "Failed to initialize RocksDB" }
            throw IllegalStateException("Failed to initialize RocksDB at ${dbDir.absolutePath}", e)
        }
    }

    private fun checkDirRights(dbDir: File) {
        if (!dbDir.canRead() || !dbDir.canWrite()) {
            throw IllegalStateException("Insufficient permissions for RocksDB directory: ${dbDir.absolutePath}")
        }
    }

    private fun initDir(dbDir: File) {
        if (!dbDir.exists()) {
            logger.info { "Creating dir ${dbDir.absolutePath} for RocksDB" }
            if (!dbDir.mkdirs()) {
                throw IllegalStateException("Failed to create RocksDB directory: ${dbDir.absolutePath}")
            }
        }
    }
}
