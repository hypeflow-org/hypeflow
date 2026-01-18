<template>
    <div id="app">
        <h1>Time Series Query</h1>

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
            <div v-if="result.errors && result.errors.length" style="margin-top:16px;">
                <h3>Source errors</h3>
                <ul>
                    <li v-for="(e, idx) in result.errors" :key="idx">
                        <strong>{{ e.source }}</strong> failed: {{ e.message }}
                    </li>
                </ul>
            </div>
            <!-- ---------------- -->
        </div>
    </div>
</template>

<script>
    import SearchForm from "./components/SearchForm.vue";
    import TimeSeriesChart from "./components/TimeSeriesChart.vue";
    import axios from "axios";

    export default {
        components: { SearchForm, TimeSeriesChart },

        data() {
            return {
                loading: false,
                error: null,
                result: null
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
    #app {
        max-width: 600px;
        margin: 40px auto;
        font-family: Arial, sans-serif;
    }
</style>
