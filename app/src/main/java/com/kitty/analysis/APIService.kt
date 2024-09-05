package com.kitty.analysis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object ApiService {
    suspend fun fetchDataFromApi(user_id: String, api_num: Int): String {
        return withContext(Dispatchers.IO) {
            var s = "invalid"
            if (api_num == 1) {
                s = "https://sample-accounts-api.herokuapp.com/users/$user_id"
            } else if (api_num == 2) {
                s = "https://sample-accounts-api.herokuapp.com/users/$user_id/accounts"
            } else if (api_num == 3) {
                s = "https://sample-accounts-api.herokuapp.com/accounts/2"
            }
            val connection = URL(s).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                return@withContext response.toString()
            } else {
                return@withContext "Failed to communicate with API. Response code: $responseCode"
            }
        }
    }
}
