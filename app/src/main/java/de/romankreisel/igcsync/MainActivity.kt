package de.romankreisel.igcsync

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.findNavController

class MainActivity : AppCompatActivity(), NavController.OnDestinationChangedListener {

    private lateinit var preferences: SharedPreferences
    private lateinit var navController: NavController
    private lateinit var navHostFragment: Fragment

    companion object {
        const val REQUEST_CODE_IGC_DATA_DIRECTORY = 1000
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        this.preferences = this.getPreferences(Context.MODE_PRIVATE)

        this.navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)!!
        this.navController = this.navHostFragment.findNavController()
        this.navController.addOnDestinationChangedListener(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_IGC_DATA_DIRECTORY) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val uri = data.data
                if (uri != null) {
                    this.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    preferences.edit()
                        .putString(
                            getString(R.string.preference_igc_directory_url),
                            uri.toString()
                        )
                        .apply()
                }
            }
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
            R.id.action_settings -> {
                this@MainActivity.navController.navigate(R.id.action_to_SettingsFragment)
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        if (destination.id == R.id.FirstFragment) {
            getSupportActionBar()?.setDisplayHomeAsUpEnabled(false)
        } else {
            getSupportActionBar()?.setDisplayHomeAsUpEnabled(true)
        }
    }
}