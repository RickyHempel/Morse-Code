package com.example.ricky.morse
import android.Manifest.permission_group.SMS
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.preference.PreferenceManager.getDefaultSharedPreferences
import android.provider.Settings
import android.provider.MediaStore
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.ajts.unifiedsmslibrary.Callback.SMSCallback
import com.ajts.unifiedsmslibrary.Services.Twilio
import com.ajts.unifiedsmslibrary.SMS

import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import okhttp3.Call
import okhttp3.Response
import org.json.JSONObject
import java.lang.Math.round
import java.lang.Exception
import java.time.Duration
import java.util.*
import kotlin.concurrent.timerTask


class MainActivity : AppCompatActivity() {
    private val SAMPLE_RATE: Int=44100
    //for translate
    private var letToCodeDict: HashMap<String, String> = HashMap()
    private var codeToLetDict: HashMap<String, String> = HashMap()

    private var prefs:SharedPreferences?=null

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        prefs = getDefaultSharedPreferences(this.applicationContext)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        //vals to send
        val toPhoneNum =prefs!!.getString("to_num"," ").toString()
        val twilio_account_sid =prefs!!.getString("to_acc"," ").toString()
        val twilio_auth_token =prefs!!.getString("to_auth"," ").toString()
        val fromTwilioNum =prefs!!.getString("From_to_num"," ").toString()
        //messaging
        fab.setOnClickListener { view ->
           val input = inputText.text.toString()
            if (input.matches("(\\.|-|/|\\s)+".toRegex())){
                val trans = translateMorsecode(input)
                doTwilioSend(trans,toPhoneNum,twilio_account_sid,twilio_auth_token,fromTwilioNum)
                }
            else{
                val transtext = translateTextin(input)
                doTwilioSend(transtext,toPhoneNum,twilio_account_sid,twilio_auth_token,fromTwilioNum)
            }
        }

        mTextView.movementMethod = ScrollingMovementMethod()
    //testbutton
        testButton.setOnClickListener {
            appendTextandScroll(inputText.text.toString())
            hideKeyboard()
        }
        //To show codes
        val json = loadMorseJSON()

    //Dict
        buildDict(json)
        //show codes
        CodeButton.setOnClickListener { _ ->
            showCodes()
            hideKeyboard()
        }
            //translate input
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
        //settings
        val dotLength:Int=(100/prefs!!.getString("morse_speed","15").toInt())
        val dashLength:Int= dotLength *3
        val farnsworth =3*(100/prefs!!.getString("farnsworth_speed","15").toInt())
        val morsePitch = this.prefs!!.getString("morse_pitch","550").toInt()

        //pregenerate the sine wave soundbuffers for the dot and dash sound
        val dotSoundBuffer:ShortArray=genSineWaveSoundBuffer(morsePitch.toDouble(),dotLength)
        val dashSoundBuffer:ShortArray=genSineWaveSoundBuffer(morsePitch.toDouble(),dashLength)

        //Pause for the given number of millisec
        //and then call the onDone function
        fun pause(durationMsec:Int,onDone: () -> Unit={}){
            Log.d("DEBUG","pause: " + durationMsec)
            Timer().schedule( timerTask {
                onDone()
            }, durationMsec.toLong())
        }

        //play dash sound and pause nad then do onDone
         fun  playDash(onDone:()-> Unit={}){
            Log.d("DEBUG","playDash")
            playSoundBuffer(dashSoundBuffer,{ ->pause(dotLength,onDone)})
        }

        //play dot sound and pause and then do onDone
         fun playDot(onDone: () -> Unit={}){
            Log.d("DEBUG","playDot")
            playSoundBuffer(dotSoundBuffer,{->pause(dotLength,onDone)})
        }

         fun playString(s: String, i: Int = 0) : Unit{
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
                pause(6*farnsworth,thenFun)
            else if (c==' ')
                pause(2*farnsworth,thenFun)
        }
    //playsoundss
    PlayButton.setOnClickListener{_ ->
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
    //helps with text in scroll
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
    //hides keyboard
    private fun Activity.hideKeyboard() {
        hideKeyboard(if (currentFocus == null) View(this) else currentFocus)
    }

    private fun Context.hideKeyboard(view: View) {
        val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    //loads a jsonfile
    private fun loadMorseJSON(): JSONObject {
        val filePath = "morse.json"
        val jsonStr = application.assets.open(filePath).bufferedReader().use {
            it.readText()
        }
        val jsonObj = JSONObject(jsonStr.substring(jsonStr.indexOf("{"), jsonStr.lastIndexOf("}") + 1))
        return jsonObj
    }

    //builds a Dict
    private fun buildDict(jsonObj: JSONObject) {
        for (k in jsonObj.keys()) {
            val code = jsonObj[k].toString()
            letToCodeDict.put(k, code)
            codeToLetDict.put(code, k)
        }
    }
    //show morse codes
    private fun showCodes() {
        appendTextandScroll("Here are the codes")
        for (k in letToCodeDict.keys.sorted()) {
            appendTextandScroll("$k: ${letToCodeDict[k]}")
        }
    }
    //translate text input in
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
    ///translate morse code in
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



    //makes sinewave
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
// Twilio / Unified SMS Sending API for Android from:
// https://androidmads.blogspot.com/2017/11/unified-sms-sending-api-for-android.html

// Add this to your build.gradel app file in the dependencies:
//     compile 'com.ajts.library.unifiedsms:unifiedsmslibrary:1.0.0'
// Add this to manifest file (above app):
//     <uses-permission android:name="android.permission.INTERNET" />
// Twilio test accounts will only send to pre-verified phone numbers
//     so send to the one you registered with, or add others at the twilio site

    fun doTwilioSend(message: String, toPhoneNum: String,twilio_account_sid: String,twilio_auth_token:String,fromTwilioNum:String){
        // IF YOU HAVE A PUBLIC GIT, DO NOT, DO NOT, PUT YOUR TWILIO SID/TOKENs HERE
        // AND DO NOT CHECK IT INTO GIT!!!
        // Once you check it into a PUBLIC git, it is there for ever and will be stolen.
        // Move them to a JSON file that is in the .gitignore
        // Or make them a user setting, that the user would enter
        // In a real app, move the twilio  parts to a server, so that it cannot be stolen.
        //




        val senderName    = fromTwilioNum.toString()  // ??

        val sms = SMS();
        val twilio = Twilio(twilio_account_sid, twilio_auth_token)

        // This code was converted from Java to Kotlin
        //  and then it had to have its parameter types changed before it would work

        sms.sendSMS(twilio, senderName, toPhoneNum, message, object : SMSCallback {
            override fun onResponse(call: okhttp3.Call?, response: Response?) {
                Log.v("twilio", response.toString())
                showSnack(response.toString())
            }
            override fun onFailure(call: okhttp3.Call?, e: java.lang.Exception?) {
                Log.v("twilio", e.toString())
                showSnack(e.toString())
            }
        })
    }

    // helper function to show a quick notice
    fun showSnack(s:String) {
        Snackbar.make(this.findViewById(android.R.id.content), s, Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
    }
}



