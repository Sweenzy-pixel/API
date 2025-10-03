document.addEventListener("DOMContentLoaded", function () {
  // --- Login alerts ---
  const errorEl = document.getElementById("loginError");
  const successEl = document.getElementById("loginSuccess");
  const registeredEl = document.getElementById("loginRegistered");

  if (errorEl && errorEl.value) {
    alert("Login failed! Please check your username and password.");
  }

  if (successEl && successEl.value) {
    alert("Login successful!");
  }

  if (registeredEl && registeredEl.value) {
    alert("Successfully registered! Please log in.");
  }

  // --- Carousel logic ---
  let index = 0;
  const carousel = document.querySelector(".carousel");
  const items = document.querySelectorAll(".carousel-item");
  const total = items.length;

  if (carousel && total > 0) {
    function showSlide(i) {
      index = (i + total) % total;
      carousel.style.transform = `translateX(${-index * 100}%)`;
    }

    const nextBtn = document.querySelector(".next");
    const prevBtn = document.querySelector(".prev");

    if (nextBtn) {
      nextBtn.addEventListener("click", () => showSlide(index + 1));
    }
    if (prevBtn) {
      prevBtn.addEventListener("click", () => showSlide(index - 1));
    }

    // Auto-slide every 4 seconds
    setInterval(() => showSlide(index + 1), 4000);
  }
});
