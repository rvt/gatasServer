package nl.rvantwisk.gatas.models
enum class RestStatus {
    SUCCESS,
    FAILURE,
    TIMEOUT,
    UNAUTHORIZED,
    NETWORK_ERROR,
    UNKNOWN_ERROR
}

data class RestResult<T>(
    val source : String,
    val status: RestStatus,
    val data: T? = null,
    val message: String? = null,
    val code: Int? = null
)