export async function droppedUploadFiles(transfer: DataTransfer): Promise<File[]> {
  // 드롭 이벤트가 끝나기 전에 브라우저의 파일·엔트리 참조를 확보합니다.
  const entries = Array.from(transfer.items ?? []).filter(item => item.kind === 'file')
    .map(item => item.webkitGetAsEntry?.() ?? null)
  const fallback = Array.from(transfer.files)
  if (entries.length === 0 || entries.every(entry => entry === null)) return fallback
  if (entries.some(entry => entry === null)) throw new Error('일부 항목을 읽지 못했습니다. 파일 선택 또는 폴더 선택 버튼을 이용해 주세요.')

  async function read(entry: FileSystemEntry, parent: string): Promise<File[]> {
    const path = parent ? `${parent}/${entry.name}` : entry.name
    if (entry.isFile) {
      const file = await new Promise<File>((resolve, reject) => (entry as FileSystemFileEntry).file(resolve, reject))
      if (parent) Object.defineProperty(file, 'webkitRelativePath', { value: path })
      return [file]
    }
    if (!entry.isDirectory) return []
    const reader = (entry as FileSystemDirectoryEntry).createReader()
    const files: File[] = []
    while (true) {
      const batch = await new Promise<FileSystemEntry[]>((resolve, reject) => reader.readEntries(resolve, reject))
      if (batch.length === 0) break
      for (const child of batch) files.push(...await read(child, path))
    }
    return files
  }

  const files: File[] = []
  for (const entry of entries) files.push(...await read(entry!, ''))
  return files
}
