import { computed, onUnmounted, ref, watch, type Ref } from 'vue';
import { boundedColumnWidth, maximumColumnWidth, readColumnWidths, type CampaignTableColumn } from './campaignTableColumns';

export function useCampaignColumnResize(columns: Ref<CampaignTableColumn[]>, storageKey: Ref<string | null>) {
  const tableRef = ref<HTMLTableElement>();
  const widths = ref<Record<string, number>>({});
  const resizingColumn = ref('');
  let drag: {
    key: string; x: number; width: number; before: Record<string, number>;
    handle: HTMLElement; pointer: number; cursor: string; selection: string;
  } | undefined;
  const columnWidth = (column: CampaignTableColumn) => widths.value[column.key] ?? column.width;
  const tableStyle = computed(() => {
    if (!Object.keys(widths.value).length) return undefined;
    const width = `${columns.value.reduce((sum, column) => sum + columnWidth(column), 0)}px`;
    return { width, minWidth: width };
  });

  function persist() {
    try {
      if (storageKey.value) localStorage.setItem(storageKey.value, JSON.stringify(widths.value));
    } catch { /* Resizing also works when storage is unavailable. */ }
  }

  // Capture the fluid layout before resizing so other columns do not shift.
  function freezeVisibleWidths() {
    const next = { ...widths.value };
    for (const cell of Array.from(tableRef.value?.tHead?.rows[0]?.cells || [])) {
      const column = columns.value.find(c => c.key === cell.dataset.columnKey);
      if (column) next[column.key] = boundedColumnWidth(column, cell.getBoundingClientRect().width);
    }
    widths.value = next;
  }

  function finish(commit: boolean) {
    const current = drag;
    if (!current) return;
    drag = undefined;
    resizingColumn.value = '';
    document.body.style.cursor = current.cursor;
    document.body.style.userSelect = current.selection;
    if (current.handle.hasPointerCapture(current.pointer)) current.handle.releasePointerCapture(current.pointer);
    if (commit) persist();
    else widths.value = current.before;
  }

  function startResize(event: PointerEvent, column: CampaignTableColumn) {
    if (event.button !== 0 || drag) return;
    const handle = event.currentTarget as HTMLElement, before = { ...widths.value };
    freezeVisibleWidths();
    drag = {
      key: column.key, x: event.clientX, width: columnWidth(column), before, handle, pointer: event.pointerId,
      cursor: document.body.style.cursor, selection: document.body.style.userSelect,
    };
    resizingColumn.value = column.key;
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
    handle.focus({ preventScroll: true });
    handle.setPointerCapture(event.pointerId);
  }

  function moveResize(event: PointerEvent) {
    if (!drag || event.pointerId !== drag.pointer) return;
    const column = columns.value.find(c => c.key === drag!.key);
    if (!column) { finish(false); return; }
    widths.value = { ...widths.value, [column.key]: boundedColumnWidth(column, drag.width + event.clientX - drag.x) };
  }

  function endResize(event: PointerEvent) {
    if (drag?.pointer !== event.pointerId) return;
    moveResize(event);
    finish(true);
  }

  function cancelResize() { finish(false); }

  function resizeKey(event: KeyboardEvent, column: CampaignTableColumn) {
    if (event.key === 'Escape') {
      if (drag) { event.preventDefault(); event.stopPropagation(); finish(false); }
      return;
    }
    if (drag || !['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    event.stopPropagation();
    freezeVisibleWidths();
    const next = event.key === 'Home' ? column.minWidth : event.key === 'End' ? maximumColumnWidth
      : columnWidth(column) + (event.key === 'ArrowRight' ? 1 : -1) * (event.shiftKey ? 50 : 10);
    widths.value = { ...widths.value, [column.key]: boundedColumnWidth(column, next) };
    persist();
  }

  watch(storageKey, key => {
    finish(false);
    try { widths.value = readColumnWidths(key ? localStorage.getItem(key) : null); }
    catch { widths.value = {}; }
  }, { immediate: true });
  watch(() => columns.value.map(c => c.key).join(','), () => finish(false));
  onUnmounted(() => finish(false));
  return { tableRef, columnWidth, tableStyle, resizingColumn, startResize, moveResize, endResize, cancelResize, resizeKey };
}
