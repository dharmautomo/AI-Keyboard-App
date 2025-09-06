package org.dslul.openboard.inputmethod.latin.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenAIRepository(private val client: OpenAIClient) {
    suspend fun improve(text: String): String = withContext(Dispatchers.IO) {
        client.improveBlocking(text)
    }
}


