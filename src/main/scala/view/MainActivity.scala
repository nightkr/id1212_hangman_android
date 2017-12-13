package se.nullable.kth.id1212.hangman.android.view

import android.app.Activity
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.graphics.drawable.Animatable
import android.view.{KeyEvent, View}
import android.widget.TextView
import android.text.{TextWatcher, Editable}
import com.sdsmdg.harjot.vectormaster.VectorMasterView
import se.nullable.kth.id1212.hangman.client.controller.{ ClientController, UpdateListener }
import se.nullable.kth.id1212.hangman.proto.Packet

class MainActivity extends Activity {
  implicit val context = this

  private val clientController = new ClientController()

  private lazy val clueMsg = TR.string.clue_lbl.value
  private var clueLbl: TextView = _

  private lazy val triesMsg = TR.string.tries_lbl.value
  private var triesLbl: TextView = _
  private var triesImg: VectorMasterView = _

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    val uri = getIntent.getData
    println(s"URI: $uri")
    if (uri.getScheme != "hangman") {
      throw new IllegalArgumentException(s"URI $uri must use the hangman: scheme")
    }

    clientController.start(uri.getHost, uri.getPort.toString())
    clientController.addListener(UpdateListener)

    // type ascription is required due to SCL-10491
    val vh: TypedViewHolder.main = TypedViewHolder.setContentView(this, TR.layout.main)
    vh.try_input.addTextChangedListener(TryListener)
    clueLbl = vh.clue
    triesLbl = vh.tries_left
    triesImg = vh.hangman_image
  }

  override def onDestroy(): Unit = {
    clientController.stop()
    super.onDestroy()
  }

  object TryListener extends TextWatcher {
    override def afterTextChanged(e: Editable): Unit = {
      if (e.length() > 0) {
        clientController.tryLetter(e.toString().charAt(0))
        e.clear()
      }
    }
    def beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int): Unit = {}
    def onTextChanged(s: CharSequence, start: Int, before: Int, count: Int): Unit = {}
  }

  object UpdateListener extends UpdateListener {
    override def gameOver(win: Boolean): Unit = {
      println(":(")
    }

    override def gameStateUpdate(state: Packet.GameState): Unit = {
      runOnUiThread(new Runnable {
                      def run(): Unit = {
                        clueLbl.setText(clueMsg.format(state.clue.map(_.getOrElse('_')).mkString))
                        triesLbl.setText(triesMsg.format(state.triesRemaining))
                        for (i <- 0 to 10) {
                          triesImg.getPathModelByName(s"lives$i").setStrokeAlpha(if (i >= state.triesRemaining) 1 else 0)
                        }
                        triesImg.update()
                      }
                    }
      )
      println(state)
    }
  }
}
