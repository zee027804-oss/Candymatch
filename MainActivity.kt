package com.example.candymatch

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class MainActivity : AppCompatActivity(), GameView.Listener {

    private lateinit var game: GameView
    private lateinit var tvLevel: TextView
    private lateinit var tvScore: TextView
    private lateinit var tvMoves: TextView
    private lateinit var progress: ProgressBar

    private var level = 1
    private var interstitial: InterstitialAd? = null
    private val prefs by lazy { getSharedPreferences("candy", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        level = prefs.getInt("level", 1)
        buildUi()

        MobileAds.initialize(this) {}
        loadAd()

        game.listener = this
        startLevel()
    }

    // ---------- UI ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(size: Float, bold: Boolean = false) = TextView(this).apply {
        setTextColor(Color.WHITE)
        textSize = size
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            fitsSystemWindows = true
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF6A1B9A.toInt(), 0xFFD81B60.toInt())
            )
        }
        tvLevel = label(30f, true)
        root.addView(tvLevel)

        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(8))
        }
        tvScore = label(18f).apply { gravity = Gravity.START }
        tvMoves = label(18f).apply { gravity = Gravity.END }
        stats.addView(tvScore, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        stats.addView(tvMoves, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(stats, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        root.addView(progress, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(12)))

        game = GameView(this)
        root.addView(game, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(20) })

        setContentView(root)
    }

    private fun startLevel() {
        game.startLevel(level)
        tvLevel.text = "Level $level"
        progress.max = game.target
        progress.progress = 0
    }

    // ---------- game callbacks ----------

    override fun onStats(score: Int, moves: Int) {
        tvScore.text = "Score: $score / ${game.target}"
        tvMoves.text = "Moves: $moves"
        progress.progress = minOf(score, game.target)
    }

    override fun onWon() {
        prefs.edit().putInt("level", level + 1).apply()
        AlertDialog.Builder(this)
            .setTitle("Level $level complete! \uD83C\uDF89")
            .setMessage("Score: ${game.score}")
            .setCancelable(false)
            .setPositiveButton("Next level") { _, _ ->
                level++
                showAdThen { startLevel() }   // interstitial on every new level
            }
            .show()
    }

    override fun onLost() {
        AlertDialog.Builder(this)
            .setTitle("Out of moves")
            .setMessage("You needed ${game.target} points, you got ${game.score}.")
            .setCancelable(false)
            .setPositiveButton("Retry") { _, _ -> startLevel() }
            .show()
    }

    // ---------- AdMob interstitial ----------

    private fun loadAd() {
        InterstitialAd.load(
            this, getString(R.string.admob_interstitial_id), AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad }
                override fun onAdFailedToLoad(error: LoadAdError) { interstitial = null }
            })
    }

    private fun showAdThen(next: () -> Unit) {
        val ad = interstitial
        if (ad == null) {       // ad not ready: never block the player
            loadAd(); next(); return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitial = null; loadAd(); next()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitial = null; loadAd(); next()
            }
        }
        ad.show(this)
    }
}
