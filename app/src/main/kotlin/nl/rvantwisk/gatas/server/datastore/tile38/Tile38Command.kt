package nl.rvantwisk.gatas.server.datastore.tile38

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.output.StatusOutput
import io.lettuce.core.protocol.CommandArgs
import io.lettuce.core.protocol.ProtocolKeyword
import kotlinx.coroutines.flow.collect

/**
 * Protocol keywords needed for Tile38
 */
class NEARBY: ProtocolKeyword {
  override fun getBytes(): ByteArray = name().toByteArray()
  override fun name(): String = "NEARBY";
}
class GET: ProtocolKeyword {
  override fun getBytes(): ByteArray = name().toByteArray()
  override fun name(): String = "GET";
}

class JSET: ProtocolKeyword {
  override fun getBytes(): ByteArray = name().toByteArray()
  override fun name(): String = "JSET";
}
class FSET: ProtocolKeyword {
  override fun getBytes(): ByteArray = name().toByteArray()
  override fun name(): String = "FSET";
}

class SCAN: ProtocolKeyword {
    override fun getBytes(): ByteArray = name().toByteArray()
    override fun name(): String = "SCAN";
}

class DROP: ProtocolKeyword {
    override fun getBytes(): ByteArray = name().toByteArray()
    override fun name(): String = "DROP";
}

class OUTPUT: ProtocolKeyword {
    override fun getBytes(): ByteArray = name().toByteArray()
    override fun name(): String = "OUTPUT";
}

@OptIn(ExperimentalLettuceCoroutinesApi::class)

/**
 * Instructs Tile38 to output JSON instead of redis formats
 * Call it once when opening the connection
 *         runBlocking {
 *           connection.setJsonOutput()
 *         }
 */
suspend fun StatefulRedisConnection<String, String>.setJsonOutput() {
    val cmdArgs = CommandArgs(StringCodec.UTF8).add("json")
    with(coroutines()) {
        dispatch(
            OUTPUT(),
            StatusOutput(StringCodec.UTF8),
            cmdArgs
        ).collect()
    }
}
