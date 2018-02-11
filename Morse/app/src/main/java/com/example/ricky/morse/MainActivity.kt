package com.example.ricky.morse
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.preference.PreferenceManager.getDefaultSharedPreferences
import android.provider.MediaStore
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager

import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import org.json.JSONObject
import java.lang.Math.round
import java.time.Duration
import java.util.*
import kotlin.concurrent.timerTask


class MainActivity : AppCompatActivity() {

    private var letToCodeDict: HashMap<String, String> = HashMap()
    private var codeToLetDict: HashMap<String, String> = HashMap()
     private val dotLength:Int=50
    private val dashLength:Int= dotLength *3
    //pregenerate the sine wave soundbuffers for the dot and dash sound
    private val dotSoundBuffer:ShortArray=genSineWaveSoundBuffer(550.0,dotLength)
    private val dashSoundBuffer:ShortArray=genSineWaveSoundBuffer(550.0,dashLength)

    private var prefs:SharedPreferences?=null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getDefaultSharedPreferences(this.applicationContext)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        val morsePich=prefs!!.getString("morse_pitch","500").toInt()
        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }

        mTextView.movementMethod = ScrollingMovementMethod()
        testButton.setOnClickListener {
            appendTextandScroll(inputText.text.toString())
            hideKeyboard()
        }

        val json = loadMorseJSON()
        buildDict(json)
        CodeButton.setOnClickListener { _ ->
            showCodes()
            hideKeyboard()
        }
        TransButton.setOnClickListener { _ ->
            val input = inputText.text.toString()
            appendTextandScroll(input.toUpperCase())
            if (input.matches("(\\.|-|\\s/\\s|\\s)+".toRegex())) {
                val transMorse = translateMorsecode(input)
                appendTextandScroll(transMorse.toUpperCase())
            } else {
                val transText = translateTextin(input)
                appendTextandScroll(transText)
            }
            hideKeyboard()
        }
        PlayButton.setOnClickListener {_ ->
            val input = inputText.text.toString()
            playString(translateTextin(input),0)
        }

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }

    }

    private fun appendTextandScroll(text: String) {
        if (mTextView != null) {
            mTextView.append(text + "\n")
        }
        val layout = mTextView.layout
        if (layout != null) {
            val scrollDelta = (layout.getLineBottom(mTextView.lineCount - 1)
                    - mTextView.scrollY - mTextView.height)
            if (scrollDelta > 0)
                mTextView.scrollBy(0, scrollDelta)
        }
    }

    private fun Activity.hideKeyboard() {
        hideKeyboard(if (currentFocus == null) View(this) else currentFocus)
    }

    private fun Context.hideKeyboard(view: View) {
        val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }


    private fun loadMorseJSON(): JSONObject {
        val filePath = "morse.json"
        val jsonStr = application.assets.open(filePath).bufferedReader().use {
            it.readText()
        }
        val jsonObj = JSONObject(jsonStr.substring(jsonStr.indexOf("{"), jsonStr.lastIndexOf("}") + 1))
        return jsonObj
    }


    private fun buildDict(jsonObj: JSONObject) {
        for (k in jsonObj.keys()) {
            val code = jsonObj[k].toString()
            letToCodeDict.put(k, code)
            codeToLetDict.put(code, k)
        }
    }

    private fun showCodes() {
        appendTextandScroll("Here are the codes")
        for (k in letToCodeDict.keys.sorted()) {
            appendTextandScroll("$k: ${letToCodeDict[k]}")
        }
    }

    private fun translateTextin(input: String): String {
        var r = ""
        val s = input.toLowerCase()
        for (c in s) {
            if (c == ' ') r += "/ "
            else if (letToCodeDict.containsKey(c.toString())) r += "${letToCodeDict[c.toString()]} "
            else r += "? "
        }
        return r
    }

    private fun translateMorsecode(input: String): String {
        var r = ""
        val s = input.split("(\\s)+".toRegex())
        for (item in s) {
            r += if (item == "/") " "
            else if (codeToLetDict.containsKey(item)) codeToLetDict[item]
            else "[na]"
        }
        return r
    }

    private fun playString(s: String, i: Int = 0) : Unit{
        //s = string of "." and "-" to play
        //i is index of which char to play
        // This function is called recursivley
        if (i > s.length - 1) {
            return
        }
        var mDelay: Long = 0;
        //thenFun=lambda function that will
        //switch back to main thread and play the next char
        var thenFun: () -> Unit = { ->
            this@MainActivity.runOnUiThread(java.lang.Runnable {
                playString(s, i + 1)
            })
        }

        var c = s[i]
        Log.d("Log", "Processing pos:" + i + "char: {" +c+ "]")
        if (c=='.')
            playDot(thenFun)
        else if (c=='-')
            playDash(thenFun)
        else if (c=='/')
            pause(6*dotLength,thenFun)
        else if (c==' ')
            pause(2*dotLength,thenFun)
    }
    //play dash sound and pause nad then do onDone
    private fun  playDash(onDone:()-> Unit={}){
        Log.d("DEBUG","playDash")
        playSoundBuffer(dashSoundBuffer,{ ->pause(dotLength,onDone)})
    }
    //play dot sound and pause and then do onDone
    private fun playDot(onDone: () -> Unit={}){
        Log.d("DEBUG","playDot")
        playSoundBuffer(dotSoundBuffer,{->pause(dotLength,onDone)})
    }
    //Pause for the given number of millisec
    //and then call the onDone function
    private fun pause(durationMsec:Int,onDone: () -> Unit={}){
        Log.d("DEBUG","pause: " + durationMsec)
        Timer().schedule( timerTask {
            onDone()
        }, durationMsec.toLong())
    }

    private val SAMPLE_RATE: Int=44100
    private fun  genSineWaveSoundBuffer(frequency:Double, durationMsec: Int):ShortArray{
        val duration:Int=round((durationMsec/100.0)*SAMPLE_RATE).toInt()
        var mSound:Double
        val mBuffer = ShortArray(duration)
        //Generate Sine wave
        for (i in 0 until duration){

            mSound=Math.sin(2.0*Math.PI * i.toDouble()/(SAMPLE_RATE/frequency))
            mBuffer[i]=(mSound*java.lang.Short.MAX_VALUE).toShort()
        }
        return mBuffer

    }

    private fun playSoundBuffer(mBuffer:ShortArray,onDone: () -> Unit={}){
    //mBuffer is the sound wave buffer to play
        //onDone is a lambda function to call when the sound is done

        var minBufferSize=SAMPLE_RATE/10

        if (minBufferSize < mBuffer.size){
            minBufferSize = minBufferSize + minBufferSize *
                    (Math.round(mBuffer.size.toFloat()) / minBufferSize.toFloat()).toInt()
        }
        //Copy data into a new buffer that is the correct buffer size
        val nBuffer=ShortArray(minBufferSize)
        for (i in nBuffer.indices){
            if (i < mBuffer.size)
                nBuffer[i]=mBuffer[i]
            else
                nBuffer[i]=0
        }
        val  mAudioTrack=AudioTrack(AudioManager.STREAM_MUSIC,SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize, AudioTrack.MODE_STREAM)
        mAudioTrack.setStereoVolume(AudioTrack.getMaxVolume(),AudioTrack.getMaxVolume())
        mAudioTrack.setNotificationMarkerPosition(mBuffer.size)
        mAudioTrack.setPlaybackPositionUpdateListener(object: AudioTrack.OnPlaybackPositionUpdateListener{
            override fun onPeriodicNotification(track: AudioTrack){}

            override fun onMarkerReached(track: AudioTrack){
                Log.d("Log","Audio track end of file reached..." )
                mAudioTrack.stop()
                mAudioTrack.release()
                onDone()
            }
        })
        mAudioTrack.play()
        mAudioTrack.write(nBuffer,0,minBufferSize)
    }
}



