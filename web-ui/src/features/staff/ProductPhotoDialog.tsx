import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { ProductImage, ProductPhotoCredit } from '../../shared/ProductImage';
import { useCommand } from '../../shared/state';
import type { Product } from '../../shared/types';
import { Button, Dialog } from '../../shared/ui';

const MAX_BYTES = 5 * 1024 * 1024;

export function ProductPhotoDialog({
  product,
  onClose,
}: {
  product: Product;
  onClose: () => void;
}) {
  const command = useCommand();
  const [file, setFile] = useState<File>();
  const [preview, setPreview] = useState('');
  const [error, setError] = useState('');
  const [illustrative, setIllustrative] = useState(false);
  const [credit, setCredit] = useState('');
  const [sourceUrl, setSourceUrl] = useState('');
  const [licenseUrl, setLicenseUrl] = useState('');
  const [confirmRemove, setConfirmRemove] = useState(false);
  const path = `/equipment-types/${product.id}/photo`;

  useEffect(() => {
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => setPreview(String(reader.result));
    reader.onerror = () => setError('Không đọc được ảnh đã chọn. Hãy chọn tệp khác.');
    reader.readAsDataURL(file);
    return () => {
      if (reader.readyState === FileReader.LOADING) reader.abort();
    };
  }, [file]);

  function choose(selected?: File) {
    setFile(undefined);
    setPreview('');
    setError('');
    if (!selected) return;
    if (
      !/\.(jpe?g|png)$/i.test(selected.name) ||
      selected.size === 0 ||
      selected.size > MAX_BYTES
    ) {
      setError('Chọn tệp JPEG hoặc PNG tối đa 5 MB.');
      return;
    }
    setFile(selected);
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!file || command.busy) return;
    const body = new FormData();
    body.set('file', file);
    if (product.photo) body.set('expectedPhotoId', product.photo.id);
    body.set('illustrative', String(illustrative));
    body.set('credit', credit.trim());
    body.set('sourceUrl', sourceUrl.trim());
    body.set('licenseUrl', licenseUrl.trim());
    setError('');
    const result = await command.run(path, body, 'Đã cập nhật ảnh sản phẩm.');
    if (result.ok) onClose();
    else if (result.error) setError(result.error);
  }

  async function remove() {
    if (!product.photo) return;
    setError('');
    const result = await command.run(
      `${path}/${product.photo.id}`,
      undefined,
      'Đã gỡ ảnh sản phẩm.',
      'DELETE',
    );
    if (result.ok) onClose();
    else if (result.error) setError(result.error);
  }

  return (
    <Dialog
      title={`Ảnh sản phẩm · ${product.name}`}
      showToastError={false}
      onClose={() => !command.busy && onClose()}
    >
      <form className="form-stack" onSubmit={save}>
        {error && (
          <div role="alert" className="inline-error">
            {error}
          </div>
        )}
        <div className="product-photo-preview">
          {preview ? (
            <img
              className="product-image"
              src={preview}
              alt="Xem trước ảnh đã chọn"
              onError={() => {
                setPreview('');
                setFile(undefined);
                setError('Không xem được ảnh. Hãy chọn tệp JPEG hoặc PNG hợp lệ.');
              }}
            />
          ) : (
            <ProductImage product={product} />
          )}
        </div>
        {!file && product.photo?.illustrative && <small>Ảnh minh họa đang hiển thị</small>}
        {!file && product.photo && <ProductPhotoCredit photo={product.photo} />}
        {confirmRemove ? (
          <>
            <p>Gỡ ảnh hiện tại và hiển thị hình vẽ mặc định trong danh mục?</p>
            <div className="dialog-actions">
              <Button
                type="button"
                className="secondary"
                disabled={command.busy}
                onClick={() => setConfirmRemove(false)}
              >
                Quay lại
              </Button>
              <Button type="button" busy={command.busy} onClick={remove}>
                Gỡ ảnh
              </Button>
            </div>
          </>
        ) : (
          <>
            <label className="field">
              <span>Chọn ảnh mới</span>
              <input
                type="file"
                accept=".jpg,.jpeg,.png,image/jpeg,image/png"
                onChange={(event) => choose(event.target.files?.[0])}
                disabled={command.busy}
              />
              <small>JPEG hoặc PNG, tối đa 5 MB. Ảnh sẽ xuất hiện công khai trong danh mục.</small>
            </label>
            {file && (
              <>
                <label className="product-photo-check">
                  <input
                    type="checkbox"
                    checked={illustrative}
                    onChange={(event) => setIllustrative(event.target.checked)}
                  />
                  <span>Gắn nhãn ảnh minh họa</span>
                </label>
                <details className="product-photo-attribution">
                  <summary>Nguồn và giấy phép ảnh</summary>
                  <label className="field">
                    <span>Ghi công</span>
                    <input
                      maxLength={200}
                      value={credit}
                      onChange={(event) => setCredit(event.target.value)}
                    />
                  </label>
                  <label className="field">
                    <span>Liên kết nguồn (HTTPS)</span>
                    <input
                      type="url"
                      maxLength={1500}
                      value={sourceUrl}
                      onChange={(event) => setSourceUrl(event.target.value)}
                    />
                  </label>
                  <label className="field">
                    <span>Liên kết giấy phép (HTTPS)</span>
                    <input
                      type="url"
                      maxLength={1500}
                      value={licenseUrl}
                      onChange={(event) => setLicenseUrl(event.target.value)}
                    />
                  </label>
                </details>
              </>
            )}
            <div className="dialog-actions">
              {product.photo && (
                <Button
                  type="button"
                  className="secondary"
                  disabled={command.busy}
                  onClick={() => {
                    setError('');
                    setConfirmRemove(true);
                  }}
                >
                  Gỡ ảnh hiện tại
                </Button>
              )}
              <Button type="submit" busy={command.busy} disabled={!file}>
                Lưu ảnh
              </Button>
            </div>
          </>
        )}
      </form>
    </Dialog>
  );
}
