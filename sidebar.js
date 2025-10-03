function openSidebar() {
  const sidebar = document.getElementById("sidebar");
  const menuBtn = document.getElementById("menuBtn");

  sidebar.style.width = "250px"; 
  menuBtn.style.display = "none"; 
}

function closeSidebar() {
  const sidebar = document.getElementById("sidebar");
  const menuBtn = document.getElementById("menuBtn");

  sidebar.style.width = "0"; 
  menuBtn.style.display = "block"; 
}
