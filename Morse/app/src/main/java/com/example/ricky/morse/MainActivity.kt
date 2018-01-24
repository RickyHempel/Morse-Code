package com.example.ricky.morse

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.text.method.ScrollingMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager

import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }

      mTextView.movementMethod=ScrollingMovementMethod()
        testButton.setOnClickListener {
            appendTextandScroll(inputText.text.toString())
            hideKeyboard()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun appendTextandScroll(text: String) {
            if (mTextView !=null){
                mTextView.append(text+"\n")
            }
        val layout = mTextView.layout
        if (layout != null)
        {
            val scrollDelta=(layout.getLineBottom(mTextView.lineCount -1)
                    - mTextView.scrollY - mTextView.height)
            if (scrollDelta > 0)
                mTextView.scrollBy(0,scrollDelta)
        }
    }

    private fun Activity.hideKeyboard() {
        hideKeyboard(if (currentFocus==null) View(this)else currentFocus)
    }

    private fun Context.hideKeyboard(view: View){
        val inputMethodManager=getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
         inputMethodManager.hideSoftInputFromWindow(view.windowToken,0)
    }
}
