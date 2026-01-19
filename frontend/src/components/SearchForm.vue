<template>
    <form @submit.prevent="submitForm">
        <div>
            <label>Word:</label>
            <input v-model="word" required />
        </div>

        <div>
            <label>Start Date:</label>
            <input type="date" v-model="startDate" required />
        </div>

        <div>
            <label>End Date:</label>
            <input type="date" v-model="endDate" required />
        </div>

        <div>
            <label>Sources:</label><br />


            
            <!-- dynamic checkboxes from fetched sources -->
            <template v-if="sourcesList.length">
                <label v-for="s in sourcesList" :key="s.id" style="display:block; margin-bottom:6px;">
                    <input type="checkbox"
                           :value="s.id"
                           v-model="sources" />
                    {{ s.title }}
                
                <!-- --- -->
                <span class="info-wrapper"
                      @mouseenter="showTooltip(s.id)"
                      @mouseleave="hideTooltip">
                    <span class="info" @click="openDocs(s.docsUrl)">
                        (i)
                    </span>

                    <div v-if="activeTooltip === s.id" class="tooltip">
                        {{ buildTooltip(s) }}
                    </div>
                </span>
                <!-- --- -->
                
                </label>
            </template>

            <div v-else>
                Loading sources...
            </div>
        </div>

        <div v-if="error" style="color: red; margin-bottom: 12px;">{{ error }}</div>

        <button type="submit" :disabled="loading || cooldownActive">Search</button>
    </form>



</template>

<script>
    import axios from "axios";

    export default {
        props: {
            loading: {
                type: Boolean,
                default: false
            }
        },

        data() {
            return {
                word: "",
                startDate: "",
                endDate: "",
                sources: [],
                error: "",
                sourcesList: [], // fetched from backend
                activeTooltip: null,
                cooldownActive: false
            };
        },

        mounted() {
            this.fetchSources();
        },

        methods: {
            async fetchSources() {
                try {
                    const resp = await axios.get("/api/sources");
                    // keep only enabled sources
                    this.sourcesList = (resp.data || []).filter(s => s.enabled !== false);
                } catch (err) {
                    console.error("Failed to load sources:", err);
                }
            },

            buildTooltip(s) {
                const parts = [];
                if (s.description) parts.push(s.description);
                if (s.unit) parts.push(`Unit: ${s.unit}`);
                if (s.maxRangeDays) parts.push(`Maximum range of days: ${s.maxRangeDays}`);
                if (s.rateLimitNote) parts.push(`Limits: ${s.rateLimitNote}`);
                if (s.docsUrl) parts.push(`Click (i) for more information`);
                return parts.join("\n");
            },

            submitForm() {
                this.error = "";
                if (this.startDate && this.endDate && this.endDate < this.startDate) {
                    this.error = "End date must be after start date.";
                    return;
                }

                // empty array means "all sources"
                this.$emit("search", {
                    word: this.word,
                    startDate: this.startDate,
                    endDate: this.endDate,
                    sources: this.sources
                });

                // 3-second cooldown
                this.cooldownActive = true;
                setTimeout(() => {
                    this.cooldownActive = false;
                }, 3000);
            },  

            showTooltip(id) {
                this.activeTooltip = id;
            },

            hideTooltip() {
                this.activeTooltip = null;
            },

            openDocs(url) {
                if (url) window.open(url, "_blank");
            },
        }
    };
</script>

<style scoped>
    form div {
        margin-bottom: 12px;
    }
    button {
        padding: 6px 12px;
    }
    .info {
        color: #0077cc;
    }

    .info-wrapper {
        position: relative;
        display: inline-block;
    }

    .tooltip {
        position: absolute;
        top: 50%;
        left: 100%;
        transform: translateY(-50%);
        margin-left: 8px;

        min-width: 220px;
        max-width: 360px;

        z-index: 1000;
        padding: 6px 8px;
        max-width: 260px;
        background: #f3f3f3;
        border: 1px solid #ddd;
        border-radius: 4px;
        white-space: pre-line;
        font-size: 12px;
        box-shadow: 0 2px 6px rgba(0,0,0,0.15);
    }


    .info {
        color: #0077cc;
        cursor: pointer;
        font-weight: bold;
        margin-left: 8px;
    }
</style>