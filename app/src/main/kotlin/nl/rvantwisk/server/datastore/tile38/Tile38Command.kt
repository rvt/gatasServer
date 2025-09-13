package nl.rvantwisk.server.datastore.tile38

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.output.StatusOutput
import io.lettuce.core.protocol.CommandArgs
import io.lettuce.core.protocol.ProtocolKeyword
import kotlinx.coroutines.flow.collect

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

class OUTPUT: ProtocolKeyword {
    override fun getBytes(): ByteArray = name().toByteArray()
    override fun name(): String = "OUTPUT";
}

@OptIn(ExperimentalLettuceCoroutinesApi::class)
/**
 * Instructs TIle38 to output JSON instead of redis formats
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
