<template>
    <div class="popular-words">
        <h3>Popular words</h3>

        <div v-if="loading">Loading popular words...</div>
        <div v-if="error" class="error">{{ error }}</div>

        <table v-if="!loading && words.length">
            <thead>
                <tr>
                    <th>Word</th>
                    <th>Search count</th>
                </tr>
            </thead>
            <tbody>
                <tr v-for="(item, idx) in words" :key="idx">
                    <td>{{ item.word }}</td>
                    <td>{{ item.count }}</td>
                </tr>
            </tbody>
        </table>

        <div v-if="!loading && !words.length">
            No data yet.
        </div>
    </div>
</template>

<script>
import axios from "axios";

export default {
    name: "PopularWords",

    data() {
        return {
            words: [],
            loading: false,
            error: null
        };
    },

    mounted() {
        this.fetchPopularWords();
    },

    methods: {
        async fetchPopularWords() {
            this.loading = true;
            this.error = null;

            try {
                const res = await axios.get("/api/search/history/popular");
                this.words = res.data;
            } catch (e) {
                this.error = "Failed to load popular words";
            } finally {
                this.loading = false;
            }
        }
    }
};
</script>

<style scoped>
    .popular-words {
        margin-top: 32px;
    }

    table {
        width: 100%;
        border-collapse: collapse;
        margin-top: 8px;
    }

    th,
    td {
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
