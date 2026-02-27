package com.signpilot.data.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date

class FirebaseLearningRepository private constructor() {

    private val db = FirebaseFirestore.getInstance()

    companion object {
        @Volatile
        private var instance: FirebaseLearningRepository? = null

        fun getInstance() = instance ?: synchronized(this) {
            instance ?: FirebaseLearningRepository().also { instance = it }
        }
    }

    data class LearningSession(
        val userId: String,
        val taskId: String,
        val taskName: String,
        val startTime: Date = Date(),
        var endTime: Date? = null,
        var stepsCompleted: Int = 0,
        var totalSteps: Int = 0,
        var mistakes: List<MistakeRecord> = emptyList(),
        var finalSuccess: Boolean = false
    )

    data class MistakeRecord(
        val stepIndex: Int,
        val mistakeType: String,
        val timestamp: Date = Date(),
        val context: String = ""
    )

    suspend fun startSession(session: LearningSession): String =
        withContext(Dispatchers.IO) {
            val docRef = db.collection("users")
                .document(session.userId)
                .collection("learning_history")
                .document()

            val data = hashMapOf(
                "task_id" to session.taskId,
                "task_name" to session.taskName,
                "start_time" to session.startTime,
                "status" to "in_progress",
                "steps_completed" to 0,
                "mistakes" to arrayListOf<String>()
            )

            docRef.set(data).await()
            docRef.id
        }

    suspend fun updateProgress(
        userId: String,
        sessionId: String,
        step: Int,
        success: Boolean,
        mistake: MistakeRecord? = null
    ) = withContext(Dispatchers.IO) {
        val ref = db.collection("users")
            .document(userId)
            .collection("learning_history")
            .document(sessionId)

        val updates = hashMapOf<String, Any>(
            "steps_completed" to FieldValue.increment(1),
            "last_update" to Date()
        )

        if (mistake != null) {
            updates["mistakes"] = FieldValue.arrayUnion(
                hashMapOf(
                    "step" to mistake.stepIndex,
                    "type" to mistake.mistakeType,
                    "time" to Date()
                )
            )
        }

        ref.update(updates).await()
    }

    suspend fun recordInteraction(userId: String, sessionId: String, intentType: String, success: Boolean) =
        withContext(Dispatchers.IO) {
            // 简化记录
        }

    suspend fun completeSession(userId: String, sessionId: String, success: Boolean) =
        withContext(Dispatchers.IO) {
            db.collection("users")
                .document(userId)
                .collection("learning_history")
                .document(sessionId)
                .update(
                    mapOf(
                        "status" to if (success) "completed" else "failed",
                        "end_time" to Date(),
                        "final_success" to success
                    )
                ).await()
        }
}
