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
            <label>
                <input type="checkbox" value="NewsAPI" v-model="sources" />
                NewsAPI
            </label>
            <label>
                <input type="checkbox" value="Wikipedia" v-model="sources" />
                Wikipedia
            </label>
        </div>

        <div v-if="error" style="color: red; margin-bottom: 12px;">{{ error }}</div>

        <button type="submit" :disabled="sources.length === 0">Search</button>
    </form>
</template>

<script>
    export default {
        data() {
            return {
                word: "",
                startDate: "",
                endDate: "",
                sources: [],
                error: ""
            };
        },

        methods: {
            submitForm() {

                this.error = "";
                if (this.startDate && this.endDate && this.endDate < this.startDate) {
                    this.error = "End date must be after start date.";
                    return;
                }

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
</style>