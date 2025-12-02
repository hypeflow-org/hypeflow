<template>
    <div>
        <canvas ref="chart"></canvas>
    </div>
</template>

<script>
    import {
        Chart,
        LineController,
        LineElement,
        PointElement,
        LinearScale,
        CategoryScale,
        Title,
        Tooltip,
        Legend
    } from "chart.js";

    Chart.register(
        LineController,
        LineElement,
        PointElement,
        LinearScale,
        CategoryScale,
        Title,
        Tooltip,
        Legend
    );

    export default {
        name: "TimeSeriesChart",

        props: ["data"],

        mounted() {
            const labels = this.data.map(d => d.date);
            const values = this.data.map(d => d.mentions);

            new Chart(this.$refs.chart, {
                type: "line",
                data: {
                    labels,
                    datasets: [
                        {
                            label: "Daily Statistics",
                            data: values
                        }
                    ]
                }
            });
        },

        beforeUnmount() {
            if (this.chart) this.chart.destroy();
        }
    };
</script>