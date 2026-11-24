import { useState } from 'react';
import type { Product } from './types';
import { DeviceArt } from './DeviceArt';

export function ProductImage({ product }: { product?: Product }) {
  const [failed, setFailed] = useState<string>();
  if (!product?.photo || product.photo.id === failed) return <DeviceArt kind={product?.icon} />;
  const { id, illustrative } = product.photo;
  return (
    <img
      className="product-image"
      src={`/api/catalog/${product.id}/photo/${id}`}
      alt={illustrative ? `Ảnh minh họa: ${product.name}` : product.name}
      onError={() => setFailed(id)}
      loading="lazy"
      decoding="async"
    />
  );
}

export function ProductPhotoCredit({ photo }: { photo: NonNullable<Product['photo']> }) {
  if (!photo.credit && !photo.sourceUrl && !photo.licenseUrl) return null;
  return (
    <div className="product-photo-credit">
      {photo.credit && <span>{photo.credit}</span>}
      {photo.sourceUrl && (
        <a href={photo.sourceUrl} target="_blank" rel="noreferrer">
          Nguồn ảnh
        </a>
      )}
      {photo.licenseUrl && (
        <a href={photo.licenseUrl} target="_blank" rel="noreferrer">
          Giấy phép
        </a>
      )}
      <span>Ảnh đã được chuẩn hóa</span>
    </div>
  );
}
