const searchInput = document.getElementById('search-input');
const clearBtn = document.getElementById('clear-btn');
const loader = document.getElementById('loader');
const modsGrid = document.getElementById('mods-grid');
const resultsMeta = document.getElementById('results-meta');
const resultsCount = document.getElementById('results-count');

let debounceTimer;

searchInput.addEventListener('input', () => {
    const query = searchInput.value.trim();
    
    if (query.length > 0) {
        clearBtn.classList.add('visible');
    } else {
        clearBtn.classList.remove('visible');
    }

    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => {
        performSearch(query);
    }, 300);
});

clearBtn.addEventListener('click', () => {
    searchInput.value = '';
    clearBtn.classList.remove('visible');
    performSearch('');
});

function searchMod(term) {
    searchInput.value = term;
    clearBtn.classList.add('visible');
    performSearch(term);
}

async function performSearch(query) {
    if (!query) {
        // Restore welcome card
        resultsMeta.style.display = 'none';
        loader.style.display = 'none';
        modsGrid.innerHTML = `
            <div class="welcome-card card">
                <div class="welcome-icon-wrapper">
                    <i class="fa-solid fa-wand-magic-sparkles"></i>
                </div>
                <h3>Start Searching</h3>
                <p>Enter the name of a Fabric or Forge mod to check if it's supported by Verg Connector on NeoForge. We query Modrinth dynamically to get the latest mod details.</p>
                <div class="featured-searches">
                    <span>Try searching:</span>
                    <button class="search-tag" onclick="searchMod('sodium')">Sodium</button>
                    <button class="search-tag" onclick="searchMod('lithium')">Lithium</button>
                    <button class="search-tag" onclick="searchMod('iris')">Iris Shaders</button>
                    <button class="search-tag" onclick="searchMod('jei')">JEI</button>
                </div>
            </div>
        `;
        return;
    }

    loader.style.display = 'flex';
    modsGrid.innerHTML = '';
    resultsMeta.style.display = 'none';

    try {
        const facets = encodeURIComponent('[["project_type:mod"]]');
        const url = `https://api.modrinth.com/v2/search?query=${encodeURIComponent(query)}&limit=12&facets=${facets}`;
        const response = await fetch(url, {
            headers: {
                'User-Agent': 'VergConnector/CompatibilityHub/1.0'
            }
        });
        
        if (!response.ok) {
            throw new Error('Network response was not ok');
        }

        const data = await response.json();
        renderResults(data.hits);
    } catch (error) {
        console.error('Error fetching search results:', error);
        loader.style.display = 'none';
        modsGrid.innerHTML = `
            <div class="empty-state">
                <i class="fa-solid fa-circle-exclamation" style="color: var(--danger)"></i>
                <h3>Search Failed</h3>
                <p>Could not connect to the Modrinth database. Please check your connection and try again.</p>
            </div>
        `;
    }
}

function renderResults(hits) {
    loader.style.display = 'none';
    resultsMeta.style.display = 'flex';
    resultsCount.textContent = `Found ${hits.length} mod${hits.length === 1 ? '' : 's'}`;

    if (hits.length === 0) {
        modsGrid.innerHTML = `
            <div class="empty-state">
                <i class="fa-solid fa-face-frown"></i>
                <h3>No Mods Found</h3>
                <p>We couldn't find any mods matching your search query. Try another name.</p>
            </div>
        `;
        return;
    }

    let html = '';
    hits.forEach(hit => {
        const compatibility = evaluateCompatibility(hit);
        
        const downloadsFormatted = new Intl.NumberFormat().format(hit.downloads);
        const icon = hit.icon_url || 'https://placehold.co/64x64/10101a/a855f7?text=Mod';
        
        const categoriesHtml = hit.categories
            ? hit.categories.slice(0, 3).map(cat => `<span class="category-tag">${cat}</span>`).join('')
            : '';

        let alertBoxHtml = '';
        if (compatibility.note) {
            alertBoxHtml = `
                <div class="alert-note">
                    <i class="fa-solid fa-circle-info"></i>
                    <div class="alert-text">${compatibility.note}</div>
                </div>
            `;
        }

        html += `
            <div class="card mod-card ${compatibility.cardClass}">
                <img src="${icon}" alt="${hit.title} Icon" class="mod-icon" onerror="this.src='https://placehold.co/64x64/10101a/a855f7?text=Mod'">
                <div class="mod-info-wrapper">
                    <div class="mod-title-row">
                        <div>
                            <h3 class="mod-title">${hit.title} <span class="mod-author">by ${hit.author}</span></h3>
                        </div>
                        <span class="status-badge">
                            <i class="${compatibility.iconClass}"></i> ${compatibility.statusText}
                        </span>
                    </div>
                    <p class="mod-description">${hit.description || 'No description provided.'}</p>
                    <div class="mod-meta-row">
                        <div class="meta-item">
                            <i class="fa-solid fa-download"></i>
                            <span>${downloadsFormatted} downloads</span>
                        </div>
                        <div class="categories-tags">
                            ${categoriesHtml}
                        </div>
                    </div>
                    ${alertBoxHtml}
                </div>
            </div>
        `;
    });

    modsGrid.innerHTML = html;
}

function evaluateCompatibility(hit) {
    const id = hit.slug ? hit.slug.toLowerCase() : '';
    const title = hit.title ? hit.title.toLowerCase() : '';
    
    // Check if the mod supports neoforge/forge natively on Modrinth
    const modLoaders = hit.categories || [];
    const hasNativeNeoForge = modLoaders.includes('neoforge');
    const hasNativeForge = modLoaders.includes('forge');
    const supportsFabric = modLoaders.includes('fabric');

    // 1. Check specific problematic mods
    if (id === 'sodium' || title.includes('sodium')) {
        return {
            statusText: 'Native Port Recommended',
            cardClass: 'status-recommended',
            iconClass: 'fa-solid fa-shuffle',
            note: 'Official Fabric <strong>Sodium</strong> uses deep ASM/Mixin modifications that conflict with NeoForge\'s window configuration and early loading screen. Use <strong>Embeddium</strong> (the optimized NeoForge port of Sodium) in your mods folder instead for 100% bug-free performance!'
        };
    }
    
    if (id === 'iris' || id === 'oculus' || title.includes('iris shaders') || title.includes('oculus')) {
        return {
            statusText: 'Native Port Recommended',
            cardClass: 'status-recommended',
            iconClass: 'fa-solid fa-shuffle',
            note: 'Fabric <strong>Iris Shaders</strong> conflicts with the window lifecycle and graphics engine configurations. Install the native NeoForge port <strong>Oculus</strong> along with <strong>Embeddium</strong> to run shaders safely.'
        };
    }

    if (id === 'indium' || id === 'canvas') {
        return {
            statusText: 'Built-in Support',
            cardClass: 'status-supported',
            iconClass: 'fa-solid fa-circle-check',
            note: '<strong>Indium</strong> is not needed! Verg Connector implements the <strong>Fabric Renderer API (FRAPI v1)</strong> natively, meaning Fabric mods utilizing custom block renderers will work automatically.'
        };
    }

    if (id === 'fabric-api' || id === 'fabric-language-kotlin') {
        return {
            statusText: 'Built-in Support',
            cardClass: 'status-supported',
            iconClass: 'fa-solid fa-circle-check',
            note: 'Verg Connector includes built-in shims for the <strong>Fabric API</strong> (including Custom Packet Networking, Block Colors, Creative Tabs, and Lifecycle Events).'
        };
    }

    // 2. Check if a native NeoForge/Forge version exists
    if (hasNativeNeoForge || hasNativeForge) {
        return {
            statusText: 'Fully Supported',
            cardClass: 'status-supported',
            iconClass: 'fa-solid fa-circle-check',
            note: `This mod has a native <strong>${hasNativeNeoForge ? 'NeoForge' : 'Forge'}</strong> version available. For the best stability, run the native version directly in your mods folder.`
        };
    }

    // 3. Fabric-only mods
    if (supportsFabric && !hasNativeNeoForge && !hasNativeForge) {
        // Certain heavy rendering optimization categories might be partial
        const isRenderOptimization = id.includes('c2me') || id.includes('lithium') || id.includes('sodium-extra') || id.includes('phosphor') || id.includes('krypton');
        
        if (isRenderOptimization) {
            return {
                statusText: 'Partial / Testing',
                cardClass: 'status-partial',
                iconClass: 'fa-solid fa-triangle-exclamation',
                note: 'Low-level performance/chunk optimization mods might require custom mixin configs. Verg Connector supports most Fabric mods, but optimization mods should be verified individually.'
            };
        }

        return {
            statusText: 'Supported via Bridge',
            cardClass: 'status-supported',
            iconClass: 'fa-solid fa-circle-check',
            note: 'This is a Fabric-only mod. Verg Connector will load it dynamically, generating virtual mod metadata and bridging its API endpoints.'
        };
    }

    // 4. Default fallback
    return {
        statusText: 'Supported',
        cardClass: 'status-supported',
        iconClass: 'fa-solid fa-circle-check',
        note: 'This mod is fully compatible with Verg Connector\'s mod loading and lifecycle systems.'
    };
}
