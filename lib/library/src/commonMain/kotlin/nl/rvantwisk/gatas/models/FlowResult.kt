package nl.rvantwisk.gatas.models
enum class FlowStatus {
    SUCCESS,
    FAILURE,
    TIMEOUT,
    UNAUTHORIZED,
    NETWORK_ERROR,
    UNKNOWN_ERROR
}

data class FlowResult<T>(
  val source : String,
  val status: FlowStatus,
  val data: T? = null,
  val message: String? = null,
  val code: Int? = null
)
