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

        props: {
            perSource: {
                type: Array,
                default: () => []
            },
            totalSeries: {
                type: Array,
                default: () => []
            }
        },

        data() {
            return {
                chart: null,
                COLORS: [
                    "#1f77b4",
                    "#ff7f0e",
                    "#2ca02c",
                    "#d62728",
                    "#9467bd",
                    "#8c564b",
                    "#e377c2",
                    "#7f7f7f",
                    "#bcbd22",
                    "#17becf"
                ]
            };
        },

        mounted() {
            this.createChart();
        },

        watch: {
            perSource: {
                deep: true,
                handler() {
                    this.updateChart();
                }
            },
            totalSeries: {
                deep: true,
                handler() {
                    this.updateChart();
                }
            }
        },

        methods: {
            createChart() {
                const { labels, datasets } = this.buildChartData();

                this.chart = new Chart(this.$refs.chart, {
                    type: "line",
                    data: {
                        labels,
                        datasets
                    },
                    options: {
                        responsive: true,
                        plugins: {
                            legend: {
                                display: true,
                                position: "top"
                            },
                            tooltip: {
                                mode: "index",
                                intersect: false
                            }
                        },
                        interaction: {
                            mode: "nearest",
                            axis: "x",
                            intersect: false
                        },
                        scales: {
                            x: {
                                ticks: { autoSkip: true }
                            },
                            y: {
                                beginAtZero: true
                            }
                        }
                    }
                });
            },

            updateChart() {
                if (!this.chart) {
                    this.createChart();
                    return;
                }

                const { labels, datasets } = this.buildChartData();
                this.chart.data.labels = labels;
                // replace datasets entirely for simplicity
                this.chart.data.datasets = datasets;
                this.chart.update();
            },

            buildChartData() {
                // Use totalSeries as the canonical X-axis (aggregated)
                const labels = (this.totalSeries || []).map(d => d.date);

                // Build per-source datasets, skipping sources with errors or missing dailyStatistics
                const validSources = (this.perSource || []).filter(
                    s => !s.error && Array.isArray(s.dailyStatistics)
                );

                const datasets = validSources.map((s, idx) => {
                    const color = this.COLORS[idx % this.COLORS.length];
                    const dataMap = s.dailyStatistics.map(d => d.mentions);
                    return {
                        label: s.source, // you might want to use displayName from /api/sources later
                        data: dataMap,
                        borderColor: color,
                        backgroundColor: color,
                        borderWidth: 2,
                        pointRadius: 3,
                        tension: 0.2,
                        fill: false
                    };
                });

                // Add aggregated total line as the last dataset with distinct style
                if (labels.length) {
                    const totalData = (this.totalSeries || []).map(d => d.mentions);
                    datasets.push({
                        label: "Total",
                        data: totalData,
                        borderColor: "#000000",
                        backgroundColor: "#000000",
                        borderWidth: 3,
                        pointRadius: 0,
                        tension: 0.15,
                        borderDash: [6, 4],
                        fill: false
                    });
                }

                return { labels, datasets };
            }
        },

        beforeUnmount() {
            if (this.chart) {
                this.chart.destroy();
                this.chart = null;
            }
        }
    };
</script>

<style scoped>
    canvas {
        width: 100% !important;
        height: 360px !important;
    }
</style>
