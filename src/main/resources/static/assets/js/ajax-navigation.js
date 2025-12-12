/**
 * AJAX Navigation System
 * Provides smooth page transitions without full page reloads
 */

(function() {
  'use strict';

  // Create loading overlay
  const loader = document.createElement('div');
  loader.className = 'ajax-loader';
  loader.innerHTML = '<div class="spinner"></div>';
  document.body.appendChild(loader);

  // Configuration
  const config = {
    excludeSelectors: [
      'a[href^="#"]',           // Anchor links
      'a[href^="mailto:"]',     // Email links
      'a[href^="tel:"]',         // Phone links
      'a[target="_blank"]',      // External links
      'a[download]',             // Download links
      'form',                    // Form submissions
      '.no-ajax',                // Links with no-ajax class
      'a[href*="logout"]',       // Logout links
      'a[href*="download"]'      // Download links
    ],
    animationDuration: 300
  };

  /**
   * Check if a link should be excluded from AJAX navigation
   */
  function shouldExcludeLink(link) {
    for (let selector of config.excludeSelectors) {
      if (link.matches(selector)) {
        return true;
      }
    }
    // Exclude if it's a form submission
    if (link.closest('form')) {
      return true;
    }
    return false;
  }

  /**
   * Show loading overlay
   */
  function showLoader() {
    loader.classList.add('active');
  }

  /**
   * Hide loading overlay
   */
  function hideLoader() {
    loader.classList.remove('active');
  }

  /**
   * Extract content from HTML response
   */
  function extractContent(html) {
    const parser = new DOMParser();
    const doc = parser.parseFromString(html, 'text/html');
    
    // Find the main content area (body or a specific container)
    const body = doc.body;
    if (!body) return null;

    // Exclude navbar and scripts from content replacement
    const navbar = body.querySelector('.navbar');
    const scripts = body.querySelectorAll('script');
    
    // Create a container for the new content
    const content = document.createElement('div');
    content.className = 'page-content';
    
    // Copy all body children except navbar and scripts
    Array.from(body.children).forEach(child => {
      if (child !== navbar && child.tagName !== 'SCRIPT') {
        content.appendChild(child.cloneNode(true));
      }
    });

    return {
      content: content.innerHTML,
      title: doc.title || document.title,
      navbar: navbar ? navbar.outerHTML : null
    };
  }

  /**
   * Update page content
   */
  function updatePage(data) {
    // Update title
    if (data.title) {
      document.title = data.title;
    }

    // Find main content container (body or a wrapper)
    const mainContent = document.body;
    
    // Fade out current content
    mainContent.style.opacity = '0';
    mainContent.style.transition = `opacity ${config.animationDuration}ms ease-in-out`;

    setTimeout(() => {
      // Replace content (preserve navbar)
      const navbar = document.querySelector('.navbar');
      const navbarHTML = navbar ? navbar.outerHTML : '';
      
      // Clear body content except navbar
      Array.from(mainContent.children).forEach(child => {
        if (!child.classList.contains('navbar') && !child.classList.contains('ajax-loader')) {
          child.remove();
        }
      });

      // Insert new content
      const tempDiv = document.createElement('div');
      tempDiv.innerHTML = data.content;
      
      // Append all children from temp div to body
      Array.from(tempDiv.children).forEach(child => {
        mainContent.appendChild(child);
      });

      // Re-initialize any scripts
      reinitializeScripts();

      // Fade in new content
      setTimeout(() => {
        mainContent.style.opacity = '1';
        hideLoader();
        
        // Scroll to top
        window.scrollTo({ top: 0, behavior: 'smooth' });
      }, 50);
    }, config.animationDuration);
  }

  /**
   * Re-initialize scripts after AJAX load
   */
  function reinitializeScripts() {
    // Re-run any initialization scripts
    const scripts = document.querySelectorAll('script[data-reinit]');
    scripts.forEach(script => {
      const newScript = document.createElement('script');
      newScript.textContent = script.textContent;
      script.parentNode.replaceChild(newScript, script);
    });

    // Trigger custom event for page load
    window.dispatchEvent(new Event('ajax-page-loaded'));
  }

  /**
   * Handle link clicks
   */
  function handleLinkClick(e) {
    const link = e.currentTarget;
    
    // Check if link should be excluded
    if (shouldExcludeLink(link)) {
      return; // Let browser handle normally
    }

    const href = link.getAttribute('href');
    if (!href || href === '#') {
      return;
    }

    // Prevent default navigation
    e.preventDefault();
    e.stopPropagation();

    // Show loader
    showLoader();

    // Update URL without reload
    if (window.history && window.history.pushState) {
      window.history.pushState({}, '', href);
    }

    // Fetch new page
    fetch(href, {
      headers: {
        'X-Requested-With': 'XMLHttpRequest',
        'Accept': 'text/html'
      }
    })
    .then(response => {
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      return response.text();
    })
    .then(html => {
      const data = extractContent(html);
      if (data && data.content) {
        updatePage(data);
      } else {
        // Fallback to normal navigation
        window.location.href = href;
      }
    })
    .catch(error => {
      console.error('AJAX navigation error:', error);
      hideLoader();
      // Fallback to normal navigation
      window.location.href = href;
    });
  }

  /**
   * Initialize AJAX navigation
   */
  function init() {
    // Attach click handlers to all links
    document.addEventListener('click', function(e) {
      const link = e.target.closest('a');
      if (link && link.href) {
        // Only handle internal links
        const url = new URL(link.href, window.location.origin);
        if (url.origin === window.location.origin) {
          handleLinkClick(e);
        }
      }
    });

    // Handle browser back/forward buttons
    window.addEventListener('popstate', function(e) {
      showLoader();
      fetch(window.location.href, {
        headers: {
          'X-Requested-With': 'XMLHttpRequest',
          'Accept': 'text/html'
        }
      })
      .then(response => response.text())
      .then(html => {
        const data = extractContent(html);
        if (data && data.content) {
          updatePage(data);
        } else {
          window.location.reload();
        }
      })
      .catch(() => {
        window.location.reload();
      });
    });

    // Mark page as loaded
    document.body.classList.add('page-loaded');
  }

  // Initialize when DOM is ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();

