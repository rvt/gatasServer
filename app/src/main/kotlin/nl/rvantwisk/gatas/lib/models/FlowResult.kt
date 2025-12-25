package nl.rvantwisk.gatas.lib.models
enum class FlowStatus {
    SUCCESS,
    FAILURE,
    TIMEOUT,
    UNAUTHORIZED,
    NETWORK_ERROR,
    UNKNOWN_ERROR
}

/**
 * Helps data class to send a Result dataset from a FLOW that allows to inspect if the flow item was correct or not
 */
data class FlowResult<T>(
  val source : String,
  val status: FlowStatus,
  val data: T? = null,
  val message: String? = null,
  val code: Int? = null
)
