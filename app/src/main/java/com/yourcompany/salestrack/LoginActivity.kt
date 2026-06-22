package com.yourcompany.salestrack

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yourcompany.salestrack.databinding.ActivityLoginBinding
import com.yourcompany.salestrack.model.Profile
import com.yourcompany.salestrack.supabase.ProfilesRepository
import com.yourcompany.salestrack.supabase.Supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val profilesRepository = ProfilesRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Session Persistence Check
        val sharedPref = getSharedPreferences("SalesTrackSession", Context.MODE_PRIVATE)
        val savedUserId = sharedPref.getString("user_id", null)
        val savedRole = sharedPref.getString("role", null)

        if (savedUserId != null && savedRole != null) {
            routeBasedOnRole(savedRole)
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            val emailText = binding.edtEmail.text.toString().trim()
            val passwordText = binding.edtPassword.text.toString().trim()

            if (emailText.isEmpty() || passwordText.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.progressLogin.visibility = View.VISIBLE
            binding.btnLogin.isEnabled = false

            lifecycleScope.launch {
                try {
                    // Sign in with Supabase Auth
                    Supabase.client.auth.signInWith(Email) {
                        email = emailText
                        password = passwordText
                    }

                    val userId = Supabase.client.auth.currentUserOrNull()?.id
                    if (userId == null) {
                        throw Exception("Failed to retrieve authenticated user ID")
                    }

                    // Fetch role from profiles table
                    val profile = profilesRepository.getProfile(userId)
                    if (profile == null) {
                        // User has logged in, but has no profile entry. Create a default salesperson profile if not found
                        // (Usually profiles are created on signup via trigger or admin)
                        Toast.makeText(this@LoginActivity, "Profile not found. Please contact manager.", Toast.LENGTH_LONG).show()
                        Supabase.client.auth.signOut()
                        binding.progressLogin.visibility = View.GONE
                        binding.btnLogin.isEnabled = true
                        return@launch
                    }

                    // Save session details locally
                    sharedPref.edit().apply {
                        putString("user_id", profile.id)
                        putString("full_name", profile.fullName)
                        putString("role", profile.role)
                        apply()
                    }

                    Toast.makeText(this@LoginActivity, "Login Successful", Toast.LENGTH_SHORT).show()
                    routeBasedOnRole(profile.role)

                } catch (e: Exception) {
                    Toast.makeText(
                        this@LoginActivity,
                        "Authentication failed: ${e.message ?: e.toString()}",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.progressLogin.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                }
            }
        }
    }

    private fun routeBasedOnRole(role: String) {
        val intent = if (role.lowercase() == "manager") {
            Intent(this, ManagerDashboardActivity::class.java)
        } else {
            Intent(this, SalespersonHomeActivity::class.java)
        }
        startActivity(intent)
        finish()
    }
}
