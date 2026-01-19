<template>
    <div id="app">
        <h1>HypeFlow</h1>

        <!-- pass loading so SearchForm can disable the button while request runs -->
        <SearchForm @search="handleSearch" :loading="loading" />

        <div v-if="loading">Loading...</div>
        <div v-if="error" style="color: red">{{ error }}</div>

        <div v-if="result">
            <p><strong>Total mentions:</strong> {{ result.totalMentions }}</p>

            <!-- pass per-source series and the aggregated total series -->
            <TimeSeriesChart :per-source="result.perSource || []"
                             :total-series="result.dailyStatistics || []" />

            <!-- per-source errors -->
            <div v-if="result.errors && result.errors.length" ref="errorsSection" style="margin-top: 16px; color: red;">
                <h3>Source errors</h3>
                <ul>
                    <li v-for="(e, idx) in result.errors" :key="idx">
                        <strong>{{ e.source }}</strong> failed: {{ e.message }}
                    </li>
                </ul>
            </div>

        </div>

        <!-- history and popular words -->
        <PopularWords />
        <LastSearches :refresh-key="historyRefreshKey" />
    </div>
</template>

<script>
    import SearchForm from "./components/SearchForm.vue";
    import TimeSeriesChart from "./components/TimeSeriesChart.vue";
    import LastSearches from "./components/LastSearches.vue";
    import PopularWords from "./components/PopularWords.vue";
    import axios from "axios";

    export default {
        components: { SearchForm, TimeSeriesChart, LastSearches, PopularWords },

        data() {
            return {
                loading: false,
                error: null,
                result: null,
                historyRefreshKey: 0
            };
        },

        methods: {
            async handleSearch(payload) {
                this.loading = true;
                this.error = null;
                this.result = null;// clear result?

                console.log("Sending request:", payload);

                try {
                    const response = await axios.post("/api/timeseries", payload);
                    console.log("Response:", response.data);
                    this.result = response.data;

                    // scroll down to show errors
                    this.$nextTick(() => {
                        if (this.$refs.errorsSection) {
                            this.$refs.errorsSection.scrollIntoView({
                                behavior: "smooth",
                                block: "end"
                            });
                        }
                    });

                    this.historyRefreshKey++;

                } catch (err) {
                    console.error("Request failed:", err);
                    console.error("Error response:", err.response);
                    this.error = err.response?.data?.error || "Failed to fetch data: " + err.message;
                } finally {
                    this.loading = false;
                }
            }
        }
    };
</script>

<style>
    body {
        margin: 0;
        min-height: 100vh;
        background: #E6EFF3; /* side color */
    }


    #app {
        max-width: 800px;
        margin: 0 auto;
        padding: 40px 60px;
        min-height: 100vh;

        font-family: Arial, sans-serif;
        background: #ffffff;
        box-sizing: border-box;
    }
</style>
