const GITHUB_URL = 'https://github.com/sayan21m/mess-wise';
const APK_URL = 'https://github.com/sayan21m/mess-wise/raw/main/app/release/MessWise.apk';
const SITE_URL = 'https://mess-wise.web.app';
const APP_VERSION = '1.5';
const APP_VERSION_CODE = 6;

function openGitHub() {
    window.open(GITHUB_URL, '_blank', 'noopener,noreferrer');
}

function downloadApk() {
    window.open(APK_URL, '_blank', 'noopener,noreferrer');
    const toast = document.getElementById('download-toast');
    if (toast) {
        toast.classList.add('is-visible');
        toast.style.opacity = '1';
        if (window.matchMedia('(min-width: 640px)').matches) {
            toast.style.transform = 'translate(-50%, 0)';
        } else {
            toast.style.transform = 'translateY(0)';
        }
        setTimeout(() => {
            toast.classList.remove('is-visible');
            toast.style.opacity = '0';
            if (window.matchMedia('(min-width: 640px)').matches) {
                toast.style.transform = 'translate(-50%, 16px)';
            } else {
                toast.style.transform = 'translateY(1rem)';
            }
        }, 3500);
    }
}

function initSiteNav() {
    const mobileBtn = document.getElementById('mobile-menu-btn');
    const mobileMenu = document.getElementById('mobile-menu');
    const mobileClose = document.getElementById('mobile-menu-close');
    const toggleMenu = (show) => {
        if (!mobileMenu) return;
        mobileMenu.style.opacity = show ? '1' : '0';
        mobileMenu.style.pointerEvents = show ? 'all' : 'none';
        document.body.style.overflow = show ? 'hidden' : 'auto';
    };
    if (mobileBtn) mobileBtn.addEventListener('click', () => toggleMenu(true));
    if (mobileClose) mobileClose.addEventListener('click', () => toggleMenu(false));
    document.querySelectorAll('.mobile-link').forEach(link =>
        link.addEventListener('click', () => toggleMenu(false))
    );

    const backToTop = document.getElementById('back-to-top');
    const scrollProgress = document.getElementById('scroll-progress');
    const siteNav = document.getElementById('site-nav');
    const navLinks = document.querySelectorAll('.nav-link[data-section]');
    const sections = [...navLinks].map(l => document.getElementById(l.dataset.section)).filter(Boolean);

    window.addEventListener('scroll', () => {
        const winScroll = document.documentElement.scrollTop;
        const height = document.documentElement.scrollHeight - document.documentElement.clientHeight;
        if (scrollProgress && height > 0) {
            scrollProgress.style.width = ((winScroll / height) * 100) + '%';
        }
        if (siteNav) {
            if (winScroll > 40) siteNav.classList.add('scrolled');
            else siteNav.classList.remove('scrolled');
        }
        if (backToTop) {
            if (winScroll > 400) {
                backToTop.style.opacity = '1';
                backToTop.style.pointerEvents = 'all';
                backToTop.style.transform = 'translateY(0)';
            } else {
                backToTop.style.opacity = '0';
                backToTop.style.pointerEvents = 'none';
                backToTop.style.transform = 'translateY(32px)';
            }
        }
        if (sections.length) {
            let current = sections[0]?.id;
            sections.forEach(section => {
                if (section.getBoundingClientRect().top <= 120) current = section.id;
            });
            navLinks.forEach(link => {
                link.classList.toggle('active', link.dataset.section === current);
            });
        }
    }, { passive: true });

    if (backToTop) {
        backToTop.addEventListener('click', () => window.scrollTo({ top: 0, behavior: 'smooth' }));
    }

    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
        anchor.addEventListener('click', (e) => {
            const targetId = anchor.getAttribute('href');
            if (!targetId || targetId === '#') return;
            const target = document.querySelector(targetId);
            if (target) {
                e.preventDefault();
                target.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
        });
    });
}

function initLucide() {
    if (typeof lucide !== 'undefined') lucide.createIcons();
}

document.addEventListener('DOMContentLoaded', () => {
    initLucide();
    initSiteNav();
});
