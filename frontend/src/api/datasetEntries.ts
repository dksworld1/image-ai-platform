import { fetchImageDatasetTargets, fetchImages, deleteImage, saveImageDescriptions, type DatasetImage } from './images'
import { fetchFileDatasetTargets, fetchFiles, deleteFile, saveFileDescriptions, type DatasetFile } from './files'

export type EntryKind = 'images' | 'files'
export type DatasetEntry = Omit<DatasetImage, 'imageId'> & { entryId: string }
export type EntryDescriptionChange = { entryId: string; description: string | null }

export function imageEntry(image: DatasetImage): DatasetEntry {
  const { imageId, ...fields } = image
  return { ...fields, entryId: imageId }
}
export function fileEntry(file: DatasetFile): DatasetEntry {
  const { fileId, ...fields } = file
  return { ...fields, entryId: fileId, thumbnailUrl: null }
}

export const entryApis = {
  images: {
    targets: fetchImageDatasetTargets,
    list: async (datasetId: string, token: string, signal?: AbortSignal) => (await fetchImages(datasetId, token, signal)).map(imageEntry),
    remove: deleteImage,
    save: async (datasetId: string, items: EntryDescriptionChange[], token: string, signal?: AbortSignal) =>
      (await saveImageDescriptions(datasetId, items.map(item => ({ imageId: item.entryId, description: item.description })), token, signal)).map(imageEntry),
  },
  files: {
    targets: fetchFileDatasetTargets,
    list: async (datasetId: string, token: string, signal?: AbortSignal) => (await fetchFiles(datasetId, token, signal)).map(fileEntry),
    remove: deleteFile,
    save: async (datasetId: string, items: EntryDescriptionChange[], token: string, signal?: AbortSignal) =>
      (await saveFileDescriptions(datasetId, items.map(item => ({ fileId: item.entryId, description: item.description })), token, signal)).map(fileEntry),
  },
}
