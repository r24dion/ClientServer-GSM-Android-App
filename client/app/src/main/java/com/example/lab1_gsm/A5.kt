package com.example.lab1_gsm

object A5 {

    fun encrypt(message: String, keyHex: String, frameHex: String): String {
        val keyStreamGenerator = KeyStreamGenerator(keyHex, frameHex)
        keyStreamGenerator.init()

        val inputBytes = message.toByteArray()
        val output = ByteArray(inputBytes.size)

        for (i in inputBytes.indices) {
            val keyByte = keyStreamGenerator.getStreamByte()
            output[i] = (inputBytes[i].toInt() xor keyByte.toInt()).toByte()
        }

        return output.joinToString("") { "%02x".format(it) }
    }

    fun decrypt(cipherHex: String, keyHex: String, frameHex: String): String {
        val keyStreamGenerator = KeyStreamGenerator(keyHex, frameHex)
        keyStreamGenerator.init()

        val input = cipherHex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()

        val output = ByteArray(input.size)

        for (i in input.indices) {
            val ks = keyStreamGenerator.getStreamByte()
            output[i] = (input[i].toInt() xor ks.toInt()).toByte()
        }

        return String(output)
    }
}