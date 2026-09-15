package com.example.nyxa_interview.data.remote.mock

sealed class MockApiException(message: String) : Exception(message) {
    class Unauthorized : MockApiException("401 Unauthorized")
    class ServerError : MockApiException("Simulated random server failure")
    /** Server processed and settled the request, but the response never reached the client. */
    class DroppedConnection : MockApiException("Simulated dropped connection after server settle")
    class NotFound(what: String) : MockApiException("Not found: $what")
}
