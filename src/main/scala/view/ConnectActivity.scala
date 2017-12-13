package se.nullable.kth.id1212.hangman.android.view

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle

class ConnectActivity extends Activity {
  implicit val context = this

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    val intent = new Intent(this, classOf[MainActivity])
    intent.setData(new Uri.Builder().scheme("hangman").encodedAuthority("192.168.1.14:2729").build())
    startActivity(intent)
    finish()
  }
}
