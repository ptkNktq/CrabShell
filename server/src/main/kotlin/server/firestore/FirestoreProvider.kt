package server.firestore

import com.google.cloud.firestore.Firestore
import com.google.firebase.cloud.FirestoreClient

/** Firestore インスタンスの一元管理 */
object FirestoreProvider {
    val instance: Firestore by lazy { FirestoreClient.getFirestore() }
}
