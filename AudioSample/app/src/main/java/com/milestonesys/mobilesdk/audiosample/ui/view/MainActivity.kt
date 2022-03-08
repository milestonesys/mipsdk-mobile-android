package com.milestonesys.mobilesdk.audiosample.ui.view

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.milestonesys.mobilesdk.audiosample.NavGraphDirections
import com.milestonesys.mobilesdk.audiosample.R
import com.milestonesys.mobilesdk.audiosample.ui.viewmodel.CamerasViewModel

class MainActivity : AppCompatActivity() {

    private val camerasViewModel: CamerasViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.main_toolbar)
        setSupportActionBar(toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.loginFragment, R.id.camerasListFragment)
        )

        NavigationUI.setupWithNavController(toolbar, navController, appBarConfiguration)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_item_about -> onAboutPressed()
            R.id.menu_item_logout -> onLogoutPressed()
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun onAboutPressed(): Boolean {

        findNavController(R.id.nav_host_fragment)
            .navigate(
                NavGraphDirections.actionGlobalAboutFragment()
            )

        return true
    }

    private fun onLogoutPressed(): Boolean {
        camerasViewModel.logOut()

        val navController = findNavController(R.id.nav_host_fragment)
        val startDestination = navController.graph.startDestination

        navController.navigate(
            startDestination,
            null,
            navOptions {
                popUpTo = navController.graph.id
            }
        )

        return true
    }
}