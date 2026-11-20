package com.example.lab1_gsm

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

object RequestServ {

    private val client = OkHttpClient()
    private const val BASE_URL = "http://10.0.2.2:5000" // эмулятор Android → localhost

    fun requestRand(imsi: String, callback: (String?) -> Unit) {
        val json = JSONObject()
        json.put("imsi", imsi)

        val body = RequestBody.create(
            "application/json".toMediaType(),
            json.toString()
        )

        val request = Request.Builder()
            .url("$BASE_URL/auth/request")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val resp = JSONObject(it.body!!.string())
                    callback(resp.getString("rand"))
                }
            }
        })
    }

    fun verify(imsi: String, sresClient: String, callback: (Boolean, String?) -> Unit) {
        val json = JSONObject()
        json.put("imsi", imsi)
        json.put("sres", sresClient)

        val body = RequestBody.create(
            "application/json".toMediaType(),
            json.toString()
        )

        val request = Request.Builder()
            .url("$BASE_URL/auth/verify")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                callback(false, null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val resp = JSONObject(it.body!!.string())
                    val status = resp.getString("status")

                    if (status == "OK") {
                        val kc = resp.getString("kc")
                        callback(true, kc)
                    } else {
                        callback(false, null)
                    }
                }
            }
        })
    }

    fun sendMessage(
        imsi: String,
        data: String,
        frame: String,
        onResult: (String) -> Unit
    ) {
        val json = JSONObject().apply {
            put("imsi", imsi)
            put("data", data)
            put("frame", frame)
        }

        val body = json.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$BASE_URL/send")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult("ERROR: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val resp = response.body?.string() ?: ""
                onResult(resp)
            }
        })
    }
}