package com.milestonesys.mobilesdk.bookmarkssample.ui.view

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.google.android.material.snackbar.Snackbar
import com.milestonesys.mobilesdk.bookmarkssample.R
import com.milestonesys.mobilesdk.bookmarkssample.ui.viewmodel.BookmarksViewModel

class MainActivity : AppCompatActivity() {
    private val bookmarksViewModel: BookmarksViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.main_toolbar)
        setSupportActionBar(toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        bookmarksViewModel.operationStatusMessage.observe(this, {
            if (it != null) {
                Snackbar.make(findViewById(android.R.id.content), it, Snackbar.LENGTH_LONG).show()
                bookmarksViewModel.onStatusMessageDisplayed()
            }
        })

        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.loginFragment, R.id.bookmarksListFragment)
        )

        NavigationUI.setupWithNavController(toolbar, navController, appBarConfiguration)
    }
}