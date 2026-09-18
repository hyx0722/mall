<script setup>
// 通用折线图：按需引入 echarts 子模块（避免整包进 bundle）。
// 样式遵循可视化规范：2px 线宽、圆点带 2px 表面色描边、1px 实线网格、十字准星 tooltip、
// 仅在末端做直接标注（不给每个点标数值）。文字一律用中性墨水色，不穿序列色。
import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

// 图表 chrome 配色（与 element-plus 白卡片底一致，已过对比度校验）
const SURFACE = '#ffffff'
const GRID = '#e1e0d9'
const AXIS = '#c3c2b7'
const INK_MUTED = '#898781'
const INK_SECONDARY = '#52514e'
const INK_PRIMARY = '#0b0b0b'

const props = defineProps({
  // x 轴刻度（日期字符串，已按升序补齐空缺日）
  labels: { type: Array, default: () => [] },
  // [{ name, color, data:number[] }]，≥2 条时自动出图例
  series: { type: Array, default: () => [] },
  // 值口径：money=金额（¥ 千分位），count=整数计数
  valueType: { type: String, default: 'count' },
  height: { type: String, default: '260px' },
})

const el = ref(null)
const chart = shallowRef(null)

// 序列色 → 10% 透明度面积填充（淡色水洗，不做饱和色块）
function wash(color) {
  const hex = String(color).replace('#', '')
  const full = hex.length === 3 ? hex.split('').map((c) => c + c).join('') : hex
  const r = parseInt(full.slice(0, 2), 16)
  const g = parseInt(full.slice(2, 4), 16)
  const b = parseInt(full.slice(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, 0.10)`
}

const num = (v) => {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

const group = (int) => String(int).replace(/\B(?=(\d{3})+(?!\d))/g, ',')

// 轴刻度：整数刻度不拖无意义的 .00（"¥3,000" 而不是 "¥3,000.00"），非整数才保留分位
function axisValue(value) {
  const n = num(value)
  if (props.valueType === 'money') {
    const s = Number.isInteger(n) ? String(n) : n.toFixed(2)
    const [int, dec] = s.split('.')
    return `¥${group(int)}${dec ? `.${dec}` : ''}`
  }
  return group(Math.round(n))
}

// tooltip / 末端标注：读数的地方保留完整精度，只做千分位
function fullValue(value) {
  const n = num(value)
  const s = props.valueType === 'money' ? n.toFixed(2) : String(n)
  const [int, dec] = s.split('.')
  return `${props.valueType === 'money' ? '¥' : ''}${group(int)}${dec ? `.${dec}` : ''}`
}

function buildOption() {
  const labels = props.labels || []
  const series = (props.series || []).filter(Boolean)
  // 点多的时候不画圆点，否则连成一片；末端仍保留一个标记
  const showSymbol = labels.length <= 31

  return {
    // 有图例时把绘图区下压，避免图例压住曲线；
    // boundaryGap:false 让首尾刻度正好落在绘图区边缘，标签居中对齐会各溢出半个标签宽，
    // 因此 left 要留出约半个日期标签（~34px）、right 留白给末端直接标注。
    grid: { left: 34, right: 64, top: series.length >= 2 ? 34 : 16, bottom: 4, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line', lineStyle: { color: AXIS, width: 1, type: 'solid' } },
      backgroundColor: SURFACE,
      borderColor: 'rgba(11,11,11,0.10)',
      borderWidth: 1,
      padding: [8, 12],
      textStyle: { color: INK_PRIMARY, fontSize: 12 },
      extraCssText: 'border-radius:4px;box-shadow:0 2px 8px rgba(11,11,11,0.10);',
      formatter: (params) => {
        const rows = (params || []).map(
          (p) =>
            `<div style="display:flex;gap:12px;justify-content:space-between;align-items:center">
               <span style="color:${INK_SECONDARY}">
                 <span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${p.color};margin-right:6px;vertical-align:middle"></span>${p.seriesName}
               </span>
               <span style="color:${INK_PRIMARY};font-weight:600">${fullValue(p.value)}</span>
             </div>`,
        )
        const head = `<div style="color:${INK_MUTED};margin-bottom:4px">${params?.[0]?.axisValue ?? ''}</div>`
        return head + rows.join('')
      },
    },
    legend: series.length >= 2
      ? {
          top: 0,
          right: 0,
          icon: 'roundRect',
          itemWidth: 10,
          itemHeight: 10,
          itemGap: 16,
          textStyle: { color: INK_SECONDARY, fontSize: 12 },
        }
      : { show: false },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: labels,
      axisLine: { lineStyle: { color: AXIS, width: 1, type: 'solid' } },
      axisTick: { show: false },
      axisLabel: { color: INK_MUTED, fontSize: 12, hideOverlap: true },
    },
    yAxis: {
      type: 'value',
      // 计数轴不出现 0.5 这类刻度
      minInterval: props.valueType === 'money' ? 0 : 1,
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: GRID, width: 1, type: 'solid' } },
      axisLabel: { color: INK_MUTED, fontSize: 12, formatter: axisValue },
    },
    series: series.map((s) => ({
      name: s.name,
      type: 'line',
      data: s.data || [],
      smooth: false,
      showSymbol,
      symbol: 'circle',
      symbolSize: 8,
      lineStyle: { width: 2, cap: 'round', join: 'round' },
      // 2px 表面色描边，让圆点压在线上时仍可辨认
      itemStyle: { color: s.color, borderColor: SURFACE, borderWidth: 2 },
      areaStyle: { color: wash(s.color) },
      // 只在末端做直接标注；文字用墨水色，标识靠线本身的颜色
      endLabel: {
        show: (s.data || []).length > 0,
        color: INK_SECONDARY,
        fontSize: 12,
        distance: 8,
        formatter: (p) => fullValue(p.value),
      },
      emphasis: { focus: 'series', scale: false },
      animationDuration: 320,
    })),
  }
}

function render() {
  if (!chart.value) return
  // notMerge：序列数变化时不留残余
  chart.value.setOption(buildOption(), { notMerge: true })
}

onMounted(() => {
  chart.value = echarts.init(el.value, null, { renderer: 'canvas' })
  render()
  // 侧边栏折叠 / 窗口缩放都要跟着重排
  window.addEventListener('resize', resize)
})

function resize() {
  chart.value?.resize()
}

watch(() => [props.labels, props.series, props.valueType], render, { deep: true })

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart.value?.dispose()
  chart.value = null
})
</script>

<template>
  <div ref="el" class="line-chart" :style="{ height }"></div>
</template>

<style scoped>
.line-chart {
  width: 100%;
}
</style>
