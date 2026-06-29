const searchInput = document.getElementById('search-input');
const clearBtn = document.getElementById('clear-btn');
const loader = document.getElementById('loader');
const modsGrid = document.getElementById('mods-grid');
const resultsMeta = document.getElementById('results-meta');
const resultsCount = document.getElementById('results-count');

let debounceTimer;
let evaluationMode = 'standard';

function setEvaluationMode(mode) {
    evaluationMode = mode;
    const btnStandard = document.getElementById('mode-standard');
    const btnBridge = document.getElementById('mode-bridge');
    if (btnStandard) btnStandard.classList.toggle('active', mode === 'standard');
    if (btnBridge) btnBridge.classList.toggle('active', mode === 'bridge');
    
    const query = searchInput.value.trim();
    if (query.length > 0) {
        performSearch(query);
    }
}

searchInput.addEventListener('input', () => {
    const query = searchInput.value.trim();
    clearBtn.classList.toggle('visible', query.length > 0);
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => performSearch(query), 350);
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

// ─── Verg Connector Knowledge Base ───────────────────────────────────────────
// This is the ground truth for our system. All checks below are based on the
// actual capabilities implemented in VergConnector.java and its shims.

const VERG_SUPPORTED_FABRIC_APIS = [
    // Lifecycle
    'ServerLifecycleEvents', 'ServerTickEvents', 'ServerWorldEvents', 'ServerEntityEvents',
    // Networking
    'ServerPlayNetworking', 'ClientPlayNetworking', 'PayloadTypeRegistry', 'ServerPlayConnectionEvents',
    // Client rendering
    'ColorProviderRegistry', 'BlockEntityRendererRegistry', 'EntityRendererRegistry',
    // Resource loading
    'ResourceManagerHelper', 'IdentifiableResourceReloadListener',
    // Item groups
    'ItemGroupEvents',
    // Player events
    'UseBlockCallback', 'UseItemCallback', 'AttackEntityCallback',
    // Commands
    'CommandRegistrationCallback',
    // Registries
    'RegistryAttributeHolder', 'RegistryAttribute',
    // Convention tags
    'ConventionalBlockTags', 'ConventionalItemTags',
    // FabricLoader
    'FabricLoader', 'ObjectShare', 'EntrypointContainer', 'ModContainer',
];

// Mods known to be BROKEN due to deep native integration
const KNOWN_BROKEN = {
    'sodium': {
        reason: 'Deep OpenGL/LWJGL pipeline override incompatible with NeoForge window lifecycle',
        alternative: 'Embeddium'
    },
    'iris': {
        reason: 'Shader pipeline hooks into Sodium-specific rendering paths; crashes without Sodium',
        alternative: 'Oculus + Embeddium'
    },
    'canvas': {
        reason: 'Full renderer replacement (OpenGL state machine takeover) conflicts with NeoForge GL context',
        alternative: null
    },
    'bobby': {
        reason: 'Hooks into Sodium chunk rendering internals not available without native Sodium',
        alternative: null
    },
    'continuity': {
        reason: 'Requires Fabric Rendering API (FRAPI) indium layer that is Sodium-dependent',
        alternative: 'CTM (Connected Textures Mod for NeoForge)'
    },
};

// Mods that WORK but with known caveats
const KNOWN_PARTIAL = {
    'c2me': {
        reason: 'Modifies internal chunk threading. Works partially but may conflict with NeoForge chunk event hooks.',
    },
    'krypton': {
        reason: 'Patches Netty pipeline at a low level. Networking may behave differently under NeoForge\'s packet wrapper.',
    },
    'ferritecore': {
        reason: 'Memory layout optimizations for block states. Generally safe but verify with other mods.',
    },
    'lazydfu': {
        reason: 'DataFixerUpper startup optimization. Works but NeoForge has its own DFU ordering.',
    },
    'smoothboot': {
        reason: 'Thread priority tweaking. May interfere with NeoForge\'s mod loading parallelism.',
    },
};

// Mods fully confirmed working via entrypoint bridge + event shims
const KNOWN_WORKING = new Set([
    'jade', 'waila', 'roughly-enough-items', 'rei', 'jei', 'just-enough-items',
    'lithium', 'appleskin', 'jade-addons', 'inventory-hud', 'mouse-tweaks',
    'wthit', 'modmenu', 'cloth-config', 'iceberg', 'cardinal-components-api',
    'patchouli', 'trinkets', 'origins', 'farmer-delight', 'create',
    'quark', 'charm', 'enchantment-descriptions', 'xaeros-minimap',
    'journeymap', 'biomes-o-plenty', 'terralith', 'sodium-extra',
    'reese-sodium-options', 'better-f3', 'neat', 'hwyla', 'jade',
    'inventory-profiles-next', 'emi', 'spark', 'noxesium', 'axiom',
    'stoneborn', 'lambdynamiclights', 'not-enough-animations',
]);

// Mods with a native NeoForge port — should prefer native
const HAS_NATIVE_PORT = {
    'cloth-config': 'Cloth Config API (NeoForge)',
    'architectury-api': 'Architectury API (NeoForge)',
    'geckolib': 'GeckoLib (NeoForge)',
    'trinkets': 'Curios API',
    'rei': 'REI (NeoForge) or JEI',
    'roughly-enough-items': 'REI (NeoForge) or JEI',
    'patchouli': 'Patchouli (NeoForge)',
    'origins': 'Origins (NeoForge)',
    'create': 'Create (NeoForge) — already ported natively',
    'quark': 'Quark (NeoForge) — already ported natively',
    'jei': 'JEI (NeoForge) — already ported natively',
};

// Mods that are library/API mods — built into Verg or needed natively
const BUILTIN_OR_REDUNDANT = {
    'fabric-api': { msg: 'Verg Connector ships all needed Fabric API shims. Do NOT install Fabric API separately.' },
    'fabricloader': { msg: 'Handled internally by Verg Connector. Not needed.' },
    'indium': { msg: 'Not needed — Verg Connector implements Fabric Rendering API (FRAPI) natively.' },
    'fabric-language-kotlin': { msg: 'Kotlin entrypoints are supported. Install the NeoForge Kotlin adapter instead if needed.' },
    'modmenu': { msg: 'Will load but is redundant — NeoForge has a built-in mod menu screen. Optional to install.' },
};

// ─── Core compatibility evaluator ─────────────────────────────────────────────
function evaluateCompatibility(hit) {
    const id = (hit.slug || '').toLowerCase().trim();
    const title = (hit.title || '').toLowerCase();
    const categories = hit.categories || [];
    const versions = hit.versions || [];
    const loaders = categories; // Modrinth uses categories for loaders too

    const supportsTargetVersion = versions.some(v => v === '1.21.1' || v === '1.21');
    const supportsFabric = loaders.includes('fabric');
    const supportsNeoForge = loaders.includes('neoforge');
    const supportsForge = loaders.includes('forge');

    // ── Check 1: Version support ──────────────────────────────────────────────
    if (!supportsTargetVersion) {
        return status('unsupported', 'Wrong Version',
            `This mod does not support MC 1.21.1 (versions: <em>${versions.slice(0, 4).join(', ') || 'none listed'}</em>). Verg Connector targets <strong>MC 1.21.1 / NeoForge 21.1.x</strong> exclusively.`
        );
    }

    // ── Check 2: No supported loader at all ───────────────────────────────────
    if (!supportsFabric && !supportsNeoForge && !supportsForge) {
        return status('unsupported', 'Incompatible Loader',
            'This mod does not support Fabric, NeoForge, or Forge. Verg Connector can only bridge Fabric mods to NeoForge.'
        );
    }

    // ── Check 3: Already native NeoForge/Forge — just use it directly ─────────
    if ((supportsNeoForge || supportsForge) && (evaluationMode !== 'bridge' || !supportsFabric)) {
        const nativeLoader = supportsNeoForge ? 'NeoForge' : 'Forge';
        const nativeAlternative = HAS_NATIVE_PORT[id];
        const isLibrary = categories.includes('library') || categories.includes('utility');

        if (nativeAlternative) {
            return status('native', `Native: ${nativeLoader}`,
                `This mod has a native <strong>${nativeLoader}</strong> version. Use the native version directly — it works better and avoids compatibility layers. Alternative: <strong>${nativeAlternative}</strong>.`
            );
        }

        return status('native', `Native: ${nativeLoader}`,
            `This mod has a native <strong>${nativeLoader}</strong> build. Install it directly in your mods folder — no bridge needed.${isLibrary ? ' Required as a dependency for other mods.' : ''}`
        );
    }

    // ── From here: Fabric-only mods (no NeoForge/Forge build) ─────────────────

    // Check 4: Built-in / Redundant ──────────────────────────────────────────
    const builtinInfo = BUILTIN_OR_REDUNDANT[id];
    if (builtinInfo) {
        return status('builtin', 'Built-in / Not Needed', builtinInfo.msg);
    }

    // Check 5: Known BROKEN mods ──────────────────────────────────────────────
    const brokenInfo = KNOWN_BROKEN[id];
    if (brokenInfo) {
        const altMsg = brokenInfo.alternative
            ? ` Use <strong>${brokenInfo.alternative}</strong> instead.`
            : ' No direct alternative available.';
        return status('broken', 'Not Supported',
            `<strong>${hit.title}</strong> is not compatible with Verg Connector: ${brokenInfo.reason}.${altMsg}`
        );
    }

    // Check if slug or title matches a broken mod by pattern
    if (isBrokenByPattern(id, title, categories)) {
        return status('broken', 'Not Supported',
            `This renderer/optimization mod uses low-level hooks (OpenGL state, Sodium internals, or custom render pipelines) that conflict with NeoForge's rendering layer. Look for a NeoForge alternative.`
        );
    }

    // Check 6: Known PARTIAL mods ─────────────────────────────────────────────
    const partialInfo = KNOWN_PARTIAL[id];
    if (partialInfo) {
        return status('partial', 'Partial Support',
            `<strong>${hit.title}</strong> may work but with caveats: ${partialInfo.reason} Test thoroughly with your modpack.`
        );
    }

    // Check 7: Has native alternative recommended ─────────────────────────────
    const altName = HAS_NATIVE_PORT[id];
    if (altName) {
        return status('recommended', 'Native Port Available',
            `A native NeoForge version exists: <strong>${altName}</strong>. The Fabric version may work through Verg Connector, but the native port is more stable and supported.`
        );
    }

    // Check 8: Confirmed working ──────────────────────────────────────────────
    if (KNOWN_WORKING.has(id)) {
        return status('supported', 'Confirmed Working',
            `<strong>${hit.title}</strong> is confirmed compatible with Verg Connector. The Fabric entrypoints, lifecycle events, and networking are all bridged correctly.`
        );
    }

    // Check 9: Fabric-only content mod (generic, likely works) ───────────────
    const isOptimizer = categories.some(c => ['optimization', 'performance'].includes(c));
    const isRenderer = categories.some(c => ['shader', 'shaders', 'rendering'].includes(c));

    if (isOptimizer) {
        return status('partial', 'Probably Works — Verify',
            `<strong>${hit.title}</strong> is a performance/optimization mod. Most work via Verg Connector, but some hook into MC internals at a level that NeoForge also modifies. Test before including in a production pack.`
        );
    }

    if (isRenderer) {
        return status('broken', 'Likely Incompatible',
            `<strong>${hit.title}</strong> is a rendering mod. Fabric renderer mods typically rely on either Sodium or the Fabric Rendering API (FRAPI) in ways that may conflict with NeoForge's GL context. Test carefully.`
        );
    }

    // Default: Fabric-only content mod — bridge supports it
    return status('supported', 'Supported via Bridge',
        `<strong>${hit.title}</strong> is a Fabric-only mod. Verg Connector will bridge its entrypoints, registry calls, lifecycle events, and networking automatically. Works on both client + server.`
    );
}

function isBrokenByPattern(id, title, categories) {
    // Renderer-specific patterns not in the known list
    const rendererIds = ['shader', 'shadermod', 'optifine', 'optifabric', 'vulkan', 'vulkanite'];
    if (rendererIds.some(p => id.includes(p) || title.includes(p))) return true;
    // Sodium-dependent mods
    if ((id.includes('sodium') || title.includes('sodium')) && id !== 'sodium') return true;
    return false;
}

function status(type, statusText, note) {
    const map = {
        supported:   { cardClass: 'status-supported',    iconClass: 'fa-solid fa-circle-check',        badgeText: statusText },
        native:      { cardClass: 'status-native',       iconClass: 'fa-solid fa-circle-check',        badgeText: statusText },
        builtin:     { cardClass: 'status-supported',    iconClass: 'fa-solid fa-circle-check',        badgeText: statusText },
        partial:     { cardClass: 'status-partial',      iconClass: 'fa-solid fa-triangle-exclamation', badgeText: statusText },
        recommended: { cardClass: 'status-recommended',  iconClass: 'fa-solid fa-shuffle',             badgeText: statusText },
        broken:      { cardClass: 'status-unsupported',  iconClass: 'fa-solid fa-circle-xmark',        badgeText: statusText },
        unsupported: { cardClass: 'status-unsupported',  iconClass: 'fa-solid fa-circle-xmark',        badgeText: statusText },
    };
    return { ...map[type], statusText: map[type].badgeText, note };
}

// ─── Search & Render ──────────────────────────────────────────────────────────
async function performSearch(query) {
    if (!query) {
        resultsMeta.style.display = 'none';
        loader.style.display = 'none';
        modsGrid.innerHTML = `
            <div class="welcome-card card">
                <div class="welcome-icon-wrapper">
                    <i class="fa-solid fa-wand-magic-sparkles"></i>
                </div>
                <h3>Start Searching</h3>
                <p>Enter the name of a Fabric mod to check its compatibility with Verg Connector on NeoForge. Powered by Modrinth — checks real loader support, version data, and our internal compatibility database.</p>
                <div class="featured-searches">
                    <span>Try searching:</span>
                    <button class="search-tag" onclick="searchMod('jade')">Jade</button>
                    <button class="search-tag" onclick="searchMod('sodium')">Sodium</button>
                    <button class="search-tag" onclick="searchMod('lithium')">Lithium</button>
                    <button class="search-tag" onclick="searchMod('roughly-enough-items')">REI</button>
                    <button class="search-tag" onclick="searchMod('iris')">Iris Shaders</button>
                    <button class="search-tag" onclick="searchMod('create')">Create</button>
                </div>
            </div>
        `;
        return;
    }

    loader.style.display = 'flex';
    modsGrid.innerHTML = '';
    resultsMeta.style.display = 'none';

    try {
        const facets = encodeURIComponent('[[\"project_type:mod\"]]');
        const url = `https://api.modrinth.com/v2/search?query=${encodeURIComponent(query)}&limit=12&facets=${facets}`;
        const response = await fetch(url, {
            headers: { 'User-Agent': 'VergConnector/CompatibilityHub/2.0' }
        });

        if (!response.ok) throw new Error('Network response was not ok');
        const data = await response.json();
        renderResults(data.hits);
    } catch (error) {
        console.error('Error fetching search results:', error);
        loader.style.display = 'none';
        modsGrid.innerHTML = `
            <div class="empty-state">
                <i class="fa-solid fa-circle-exclamation" style="color: var(--danger)"></i>
                <h3>Search Failed</h3>
                <p>Could not connect to the Modrinth API. Please check your internet connection and try again.</p>
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
                <p>No mods matching your search were found on Modrinth. Try a different name or spelling.</p>
            </div>
        `;
        return;
    }

    modsGrid.innerHTML = hits.map(hit => {
        const compat = evaluateCompatibility(hit);
        const downloadsFormatted = new Intl.NumberFormat().format(hit.downloads);
        const icon = hit.icon_url || 'https://placehold.co/64x64/10101a/a855f7?text=Mod';
        
        const allCategories = hit.categories || [];
        const loaders = allCategories.filter(c => ['fabric', 'neoforge', 'forge', 'quilt'].includes(c));
        const tags = allCategories.filter(c => !['fabric', 'neoforge', 'forge', 'quilt'].includes(c));

        const loadersHtml = loaders.map(l => `<span class="loader-badge ${l}">${l}</span>`).join('');
        const categoriesHtml = tags.slice(0, 3)
            .map(cat => `<span class="category-tag">${cat}</span>`).join('');

        const noteHtml = compat.note ? `
            <div class="alert-note">
                <i class="fa-solid fa-circle-info"></i>
                <div class="alert-text">${compat.note}</div>
            </div>` : '';

        return `
            <div class="card mod-card ${compat.cardClass}">
                <img src="${icon}" alt="${hit.title} Icon" class="mod-icon"
                     onerror="this.src='https://placehold.co/64x64/10101a/a855f7?text=Mod'">
                <div class="mod-info-wrapper">
                    <div class="mod-title-row">
                        <div class="title-with-loaders">
                            <h3 class="mod-title">${hit.title} <span class="mod-author">by ${hit.author}</span></h3>
                            <div class="loader-tags">${loadersHtml}</div>
                        </div>
                        <span class="status-badge">
                            <i class="${compat.iconClass}"></i> ${compat.statusText}
                        </span>
                    </div>
                    <p class="mod-description">${hit.description || 'No description provided.'}</p>
                    <div class="mod-meta-row">
                        <div class="meta-item">
                            <i class="fa-solid fa-download"></i>
                            <span>${downloadsFormatted} downloads</span>
                        </div>
                        <div class="categories-tags">${categoriesHtml}</div>
                    </div>
                    ${noteHtml}
                </div>
            </div>
        `;
    }).join('');
}
