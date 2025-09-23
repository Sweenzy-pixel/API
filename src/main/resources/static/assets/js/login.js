document.addEventListener("DOMContentLoaded", function () {
  const error = document.getElementById("loginError").value;
  const success = document.getElementById("loginSuccess").value;
  const registered = document.getElementById("loginRegistered").value;

  if (error) {
    alert("Login failed! Please check your username and password.");
  }

  if (success) {
    alert("Login successful!");
  }

  if (registered) {
    alert("Successfully registered! Please log in.");
  }
});
