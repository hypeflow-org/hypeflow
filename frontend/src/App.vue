<template>
    <div id="app">
        <h1>Time Series Query</h1>

        <SearchForm @search="handleSearch" />

        <div v-if="loading">Loading...</div>
        <div v-if="error" style="color: red">{{ error }}</div>

        <div v-if="result">
            <p><strong>Total mentions:</strong> {{ result.totalMentions }}</p>
            <TimeSeriesChart :data="result.dailyStatistics" />
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
                this.result = null;

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
