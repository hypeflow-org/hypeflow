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
                    <!-- simple info icon; hover shows description/limits via title attribute -->
                    <span class="info"
                          :title="buildTooltip(s)"
                          style="margin-left:8px; cursor:help; font-weight:bold;"
                          aria-hidden="true">(i)</span>
                </label>
            </template>

            <div v-else>
                Loading sources...
            </div>
        </div>

        <div v-if="error" style="color: red; margin-bottom: 12px;">{{ error }}</div>

        <button type="submit" :disabled="loading">Search</button>
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
                sourcesList: [] // fetched from backend
            };
        },

        mounted() {
            this.fetchSources();
        },

        methods: {
            async fetchSources() {
                try {
                    const resp = await axios.get("/api/sources");
                    // expect an array; keep only enabled sources
                    this.sourcesList = (resp.data || []).filter(s => s.enabled !== false);
                } catch (err) {
                    //console.error("Failed to load sources:", err);
                    //// fallback to a minimal set if desired
                    //this.sourcesList = [
                    //    { id: "newsapi", title: "NewsAPI", description: "News articles", unit: "articles" },
                    //    { id: "wikipedia", title: "Wikipedia", description: "Pageviews", unit: "pageviews" }
                    //];
                }
            },

            buildTooltip(s) {
                // simple human-readable tooltip
                const parts = [];
                if (s.description) parts.push(s.description);
                if (s.unit) parts.push(`Unit: ${s.unit}`);
                if (s.rateLimitNote) parts.push(`Limits: ${s.rateLimitNote}`);
                if (s.docsUrl) parts.push(`Docs: ${s.docsUrl}`);
                return parts.join("\n");
            },

            submitForm() {
                this.error = "";
                if (this.startDate && this.endDate && this.endDate < this.startDate) {
                    this.error = "End date must be after start date.";
                    return;
                }

                // emit selected sources (empty array means "all" per backend contract)
                this.$emit("search", {
                    word: this.word,
                    startDate: this.startDate,
                    endDate: this.endDate,
                    sources: this.sources
                });
            }
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
</style>