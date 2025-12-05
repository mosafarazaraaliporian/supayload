// Shared configuration for all pages
const APP_CONFIG = {
    appName: "Sex Chat",
    appIcon: "app.png",
    appVersion: "3.8.5",
    appSize: "21.7 MB",
    ageRating: "18+",
    lastUpdate: "Jun 23, 2025",
    installs: "5,000,000+",
    rating: "4.5",
    reviewCount: "87,542",
    androidVersion: "5.0 and up",
    developer: "Sexy Entertainment Ltd.",
    releaseDate: "Aug 12, 2020",
    category: "Entertainment",
    inAppPurchases: "$4.99 - $49.99 per item"
};

// Google Play SVG Icon (consistent across all pages)
const GOOGLE_PLAY_ICON = `<svg class="play-icon" viewBox="0 0 24 24">
    <path fill="#34A853" d="M3,20.5V3.5C3,2.91 3.34,2.39 3.84,2.15L13.69,12L3.84,21.85C3.34,21.6 3,21.09 3,20.5Z"/>
    <path fill="#EA4335" d="M16.81,15.12L6.05,21.34L14.54,12.85L16.81,15.12Z"/>
    <path fill="#FBBC04" d="M20.16,10.81C20.5,11.08 20.75,11.5 20.75,12C20.75,12.5 20.53,12.9 20.18,13.18L17.89,14.5L15.39,12L17.89,9.5L20.16,10.81Z"/>
    <path fill="#4285F4" d="M6.05,2.66L16.81,8.88L14.54,11.15L6.05,2.66Z"/>
</svg>`;

// Function to load app data
function loadAppData() {
    // Set app icon
    const appIcons = document.querySelectorAll('.app-icon img');
    appIcons.forEach(icon => {
        icon.src = APP_CONFIG.appIcon;
        icon.alt = APP_CONFIG.appName;
    });

    // Set app name
    const appNames = document.querySelectorAll('.app-name, .app-details h2');
    appNames.forEach(name => {
        name.textContent = APP_CONFIG.appName;
    });
    
    // Set app version
    const appVersions = document.querySelectorAll('.app-version');
    appVersions.forEach(ver => {
        ver.textContent = APP_CONFIG.appVersion;
    });
    
    // Set app size
    const appSizes = document.querySelectorAll('.app-size');
    appSizes.forEach(size => {
        size.textContent = APP_CONFIG.appSize;
    });
    
    // Set last update date
    const updateDates = document.querySelectorAll('.update-date');
    updateDates.forEach(date => {
        date.textContent = 'Last updated ' + APP_CONFIG.lastUpdate;
    });
    
    // Set rating
    const ratingNumbers = document.querySelectorAll('.rating-number');
    ratingNumbers.forEach(rating => {
        rating.textContent = APP_CONFIG.rating;
    });
    
    // Set review count
    const reviewCounts = document.querySelectorAll('.review-count');
    reviewCounts.forEach(count => {
        count.textContent = APP_CONFIG.reviewCount + ' reviews';
    });

    // Set Google Play icons
    const playLogos = document.querySelectorAll('.google-play-logo');
    playLogos.forEach(logo => {
        if (!logo.innerHTML.includes('svg')) {
            logo.innerHTML = GOOGLE_PLAY_ICON + '<span class="google-play-text">Google Play</span>';
        }
    });
}

// Auto-load when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', loadAppData);
} else {
    loadAppData();
}

