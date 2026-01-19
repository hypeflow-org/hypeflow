<template>
    <div class="last-searches">
        <h3>Search history</h3>

        <div v-if="loading">Loading history…</div>
        <div v-if="error" class="error">{{ error }}</div>

        <table v-if="searches.length">
            <thead>
                <tr>
                    <th>Word</th>
                    <th>Date range</th>
                    <th>Sources</th>
                    <th>Total</th>
                    <th>Searched at</th>
                </tr>
            </thead>
            <tbody>
                <tr v-for="(s, idx) in searches" :key="idx">
                    <td>{{ s.word }}</td>
                    <td>{{ s.startDate }} → {{ s.endDate }}</td>
                    <td>{{ s.sources.join(", ") }}</td>
                    <td>{{ s.totalMentions }}</td>
                    <td>{{ formatDateTime(s.searchedAt) }}</td>
                </tr>
            </tbody>
        </table>

        <div v-else-if="!loading">No search history yet.</div>
    </div>
</template>

<script>
    import axios from "axios";

    export default {
        name: "LastSearches",

        props: {
            refreshKey: {
                type: Number,
                required: true
            }
        },

        data() {
            return {
                searches: [],
                loading: false,
                error: null
            };
        },

        watch: {
            refreshKey() {
                this.fetchLastSearches();
            }
        },

        mounted() {
            this.fetchLastSearches();
        },

        methods: {
            async fetchLastSearches() {
                this.loading = true;
                this.error = null;

                try {
                    const res = await axios.get("/api/search/history/last", {
                        params: { limit: 10 }
                    });
                    this.searches = res.data;
                } catch (e) {
                    console.error(e);
                    this.error = "Failed to load last searches";
                } finally {
                    this.loading = false;
                }
            },

            formatDateTime(value) {
                if (!value) return "";
                return new Date(value).toLocaleString();
            }
        }
    };
</script>

<style scoped>
    .last-searches {
        margin-top: 32px;
    }

    table {
        width: 100%;
        border-collapse: collapse;
        font-size: 12px;
    }

    th, td {
        border: 1px solid #ddd;
        padding: 6px 8px;
        text-align: left;
    }

    th {
        background: #f5f5f5;
    }

    .error {
        color: red;
    }
</style>
